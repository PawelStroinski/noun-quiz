(ns noun-quiz.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as resp]
            [noun-quiz.proverbs :as proverbs]
            [noun-quiz.view :as view]
            [noun-quiz.users :as users]
            [noun-quiz.config :refer [read-config]]
            [clojure.test :refer [with-test testing is]]
            [peridot.core :refer [session request follow-redirect]]
            [korma.db :refer [transaction rollback]]))

(defmacro is# [arg# form] `(let [~'% ~arg#] (is ~form) ~'%))

(defn- new-proverb []
  (-> (proverbs/read-proverbs) rand-nth))

(defn- new-icons [proverb]
  (proverbs/icons proverb (read-config) proverbs/fetch-icon))

(defn- praise []
  (-> (read-config) :prises rand-nth))

(defn- response [body]
  (-> (resp/response body) (resp/content-type "text/html") (resp/charset "utf-8")))

(defn- assemble-response [session proverb icons score & [data]]
  (-> {:score score, :icons icons}
      (merge data)
      (view/challenge)
      (response)
      (assoc :session session)
      (assoc-in [:session :proverb] proverb)
      (assoc-in [:session :icons] icons)
      (assoc-in [:session :score] score)))

(with-test
  (defroutes app-routes
             (GET "/" {{:keys [proverb icons score] :as session} :session}
               (let [proverb (or proverb (new-proverb))
                     icons (or icons (new-icons proverb))
                     score (or score proverbs/initial-score)]
                 (assemble-response session proverb icons score)))
             (POST "/" {{:keys [guess]}                                 :params
                        {:keys [proverb icons score email] :as session} :session}
               (let [guessed (proverbs/guessed? proverb guess)
                     score (proverbs/update-score score guessed)
                     reset (= proverbs/initial-tries (:tries score))
                     data {:guess guess}
                     data (if (and (not guessed) reset) {:it-was proverb, :you-typed guess} data)
                     data (if guessed {:praise (praise)} data)
                     proverb (if reset (new-proverb) proverb)
                     icons (if reset
                             (new-icons proverb)
                             (->> icons
                                  (proverbs/reveal-guessed-words proverb guess)
                                  (proverbs/reveal-one-word proverb)))]
                 (when (and email reset) (users/save-score email score))
                 (assemble-response session proverb icons score data)))
             (GET "/login" {}
               (-> (view/login-form {})
                   (response)))
             (POST "/login" {{:keys [email password]}    :params
                             {:keys [score] :as session} :session}
               (if-let [user (users/login-or-register email password)]
                 (let [response (assoc (resp/redirect "/") :session (assoc session :email email))
                       unlogged (not (:email session))
                       has-more-score-in-session (> (:points score 0) (:points user 0))]
                   (if (and unlogged has-more-score-in-session)
                     (do (users/save-score email score)
                         response)
                     (assoc-in response [:session :score]
                               (merge proverbs/initial-score
                                      score
                                      (->> (keys proverbs/initial-score) (select-keys user))))))
                 (-> (view/login-form {:wrong-password true})
                     (response))))
             (route/not-found "Not Found"))
  (declare app)
  (def ^:dynamic *all-proverbs* ["Foo bar" "Bar foo"])
  (def ^:dynamic *icons* #(map (partial hash-map :url) (re-seq #"\w+" %)))
  (with-redefs [proverbs/read-proverbs (fn [] *all-proverbs*)
                proverbs/icons (fn [proverb {:keys [always-as-text] :as config} fetcher]
                                 (is (> (count always-as-text) 10))
                                 (is (every? (partial instance? String) always-as-text))
                                 (is (= fetcher proverbs/fetch-icon))
                                 (*icons* proverb))
                view/challenge identity]
    (testing "/"
      (testing "GET returns random proverb to guess"
        (let [response #(-> (session app) (request "/") :response)
              response-data (comp :body response)]
          (is (= 200 (:status (response))))
          (is (= proverbs/initial-score (:score (response-data))))
          (doseq [proverb *all-proverbs*]
            (is (some #(= (:icons %) (*icons* proverb)) (repeatedly 10 response-data))))))
      (testing "POST returns the same proverb but with some words revealed and tries decreased"
        (with-redefs [proverbs/reveal-guessed-words (fn [proverb guess icons]
                                                      (is (= "some proverb" proverb))
                                                      (is (= "hmm") guess)
                                                      (is (= ["some-icons"] icons))
                                                      ["revealed-guessed-words"])
                      proverbs/reveal-one-word (fn [proverb icons]
                                                 (is (= "some proverb" proverb))
                                                 (is (= ["revealed-guessed-words"] icons))
                                                 ["revealed-one-word"])]
          (let [initial-request #(binding [*all-proverbs* ["some proverb"]
                                           *icons* (fn [_] ["some-icons"])]
                                  (-> (session app) (request "/")))
                response (fn [guess] (-> (initial-request)
                                         (request "/" :request-method :post, :params {:guess guess})
                                         :response))
                response-data (comp :body response)]
            (is (= 200 (:status (response ""))))
            (is (= (assoc proverbs/initial-score :tries (dec proverbs/initial-tries)) (:score (response-data ""))))
            (is (= ["revealed-one-word"] (:icons (response-data "hmm")))))))
      (testing "GET doesn't each time generate new proverb or icons or score"
        (-> (session app)
            (#(binding [*all-proverbs* ["FOO"]] (request % "/")))
            (request "/" :request-method :post, :params {:guess ""})
            (request "/")
            (is# (= ["FOO"] (get-in % [:response :body :icons])))
            (is# (= (dec proverbs/initial-tries) (get-in % [:response :body :score :tries])))))
      (testing "POST checks the guess and updates score, e.g. when couldn't guess and then guessed the next proverb"
        (-> (session app)
            (#(binding [*all-proverbs* ["bar"]] (request % "/")))
            (#(binding [*all-proverbs* ["foo"]]
               (reduce (fn [state _] (request state "/" :request-method :post, :params {:guess "hmm"}))
                       % (range (dec proverbs/initial-tries)))))
            (is# (= ["bar"] (get-in % [:response :body :icons])))
            (#(binding [*all-proverbs* ["foo"]] (request % "/" :request-method :post, :params {:guess "hmm"})))
            (is# (= [{:url "foo"}] (get-in % [:response :body :icons])))
            (is# (zero? (get-in % [:response :body :score :points])))
            (#(binding [*all-proverbs* ["bar"]] (request % "/" :request-method :post, :params {:guess "Foo."})))
            (is# (= [{:url "bar"}] (get-in % [:response :body :icons])))
            (is# (pos? (get-in % [:response :body :score :points])))))
      (testing "POST returns 'it was' and last guess"
        (-> (session app)
            (#(binding [*all-proverbs* ["bar"]] (request % "/")))
            (#(reduce (fn [state _] (request state "/" :request-method :post, :params {:guess "hmm"}))
                      % (range (dec proverbs/initial-tries))))
            (is# (not (get-in % [:response :body :it-was])))
            (is# (not (get-in % [:response :body :you-typed])))
            (is# (= "hmm" (get-in % [:response :body :guess])))
            (#(binding [*all-proverbs* ["foo"]] (request % "/" :request-method :post, :params {:guess "hmm"})))
            (is# (= "bar" (get-in % [:response :body :it-was])))
            (is# (= "hmm" (get-in % [:response :body :you-typed])))
            (is# (not (get-in % [:response :body :guess])))
            (request "/" :request-method :post, :params {:guess "foo"})
            (is# (not (get-in % [:response :body :it-was])))
            (is# (not (get-in % [:response :body :you-typed])))
            (is# (not (get-in % [:response :body :guess])))))
      (testing "POST returns a random prise after a successful guess only"
        (let [prises ["Foo!" "Bar!"]
              initial-request (fn [] (-> (session app)
                                         (#(binding [*all-proverbs* ["foo"]] (request % "/")))))
              config (read-config)]
          (with-redefs [read-config (fn [] (assoc config :prises prises))]
            (doseq [prise prises]
              (is (some #{prise} (repeatedly 10 #(-> (initial-request)
                                                     (request "/" :request-method :post, :params {:guess "foo"})
                                                     (get-in [:response :body :praise]))))))
            (reduce (fn [state _] (-> (request state "/" :request-method :post, :params {:guess "hmm"})
                                      (is# (not (get-in % [:response :body :praise])))))
                    (initial-request) (range proverbs/initial-tries))))))
    (with-redefs [view/login-form identity]
      (let [fixed-challenge #(binding [*all-proverbs* ["foo"]] (request % "/"))
            guess #(-> (fixed-challenge %) (request "/" :request-method :post, :params {:guess "foo"}))
            score #(get-in % [:response :body :score :points])
            login-as #(-> (request %2 "/login" :request-method :post
                                   :params {:email %1, :password "bar"})
                          (follow-redirect))
            login (partial login-as "foo")
            tries #(get-in % [:response :body :score :tries])
            miss-try #(-> (request % "/") (request "/" :request-method :post, :params {:guess "hmm"}))
            miss #(reduce (fn [state _] (miss-try state)) % (range proverbs/initial-tries))]
        (testing "/login"
          (testing "GET returns login form"
            (-> (session app) (request "/login")
                (is# (= 200 (get-in % [:response :status])))
                (is# (= {} (get-in % [:response :body])))))
          (testing "POST logins or registers user and saves or restores score"
            (transaction
              (rollback)
              (-> (session app) (guess) (login) (is# (pos? (score %))))
              (-> (session app) (login) (is# (pos? (score %))))
              (-> (session app) (login) (is# (pos? (get-in % [:response :body :score :guessed]))))
              (-> (session app) (guess) (guess) (#(let [big-score (score %)]
                                                   (-> (login %) (is# (= big-score (score %))))
                                                   (-> (session app) (login) (is# (= big-score (score %)))))))
              (-> (session app) (login) (is# (= proverbs/initial-tries (tries %))))
              (-> (session app) (miss-try) (login) (is# (= (dec proverbs/initial-tries) (tries %))))
              (-> (session app) (login) (guess) ((partial login-as "foo2")) (is# (zero? (score %))))))
          (testing "POST when entered wrong password"
            (transaction
              (rollback)
              (-> (session app) (guess) (login))
              (-> (session app)
                  (request "/login" :request-method :post :params {:email "foo", :password "bar2"})
                  (is# (= {:wrong-password true} (get-in % [:response :body])))
                  (request "/")
                  (is# (zero? (score %)))))))
        (testing "/"
          (testing "POST saves score of logged in user"
            (transaction
              (rollback)
              (-> (session app) (fixed-challenge) (login) (guess))
              (-> (session app) (login) (is# (pos? (score %))))
              (-> (session app) (login) (miss))
              (-> (session app) (login) (is# (pos? (get-in % [:response :body :score :missed])))))))))
    (testing "not-found route"
      (let [response (-> (session app) (request "/invalid") :response)]
        (is (= 404 (:status response)))))))

(defn- no-cache [resp]
  (-> resp
      (resp/header "Cache-Control" "no-cache, no-store")
      (resp/header "Pragma" "no-cache")
      (resp/header "Expires" -1)))

(defn- wrap-no-cache [handler]
  (fn [req]
    (if-let [resp (handler req)]
      (no-cache resp))))

(def app
  (-> app-routes
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      (wrap-no-cache)))
