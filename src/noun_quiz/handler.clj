(ns noun-quiz.handler
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as resp]
            [noun-quiz.proverbs :as proverbs]
            [noun-quiz.view :as view]
            [clojure.test :refer [with-test testing is]]
            [peridot.core :refer [session request]]))

(defn- read-config []
  (-> "config.edn" io/resource io/file slurp edn/read-string))

(defn- new-proverb []
  (-> (proverbs/read-proverbs) rand-nth))

(defn- new-icons [proverb]
  (proverbs/icons proverb (read-config) proverbs/fetch-icon))

(defn- praise []
  (-> (read-config) :prises rand-nth))

(defn- response [body]
  (-> (resp/response body) (resp/content-type "text/html") (resp/charset "utf-8")))

(defn- assemble-response [proverb icons score & [data]]
  (-> {:score score, :icons icons}
      (merge data)
      (view/challenge)
      (response)
      (assoc-in [:session :proverb] proverb)
      (assoc-in [:session :icons] icons)
      (assoc-in [:session :score] score)))

(with-test
  (defroutes app-routes
             (GET "/" {{:keys [proverb icons score]} :session}
               (let [proverb (or proverb (new-proverb))
                     icons (or icons (new-icons proverb))
                     score (or score proverbs/initial-score)]
                 (assemble-response proverb icons score)))
             (POST "/" {{:keys [guess]}               :params
                        {:keys [proverb icons score]} :session}
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
                 (assemble-response proverb icons score data)))
             (route/not-found "Not Found"))
  (def ^:dynamic *all-proverbs* ["Foo bar" "Bar foo"])
  (def ^:dynamic *icons* #(map (partial hash-map :url) (re-seq #"\w+" %)))
  (declare app)
  (with-redefs [proverbs/read-proverbs (fn [] *all-proverbs*)
                proverbs/icons (fn [proverb {:keys [always-as-text] :as config} fetcher]
                                 (is (> (count always-as-text) 10))
                                 (is (every? (partial instance? String) always-as-text))
                                 (is (= fetcher proverbs/fetch-icon))
                                 (*icons* proverb))
                view/challenge identity]
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
          (#(do (is (= ["FOO"] (get-in % [:response :body :icons])))
                (is (= (dec proverbs/initial-tries) (get-in % [:response :body :score :tries])))))))
    (testing "POST checks the guess and updates score, e.g. when couldn't guess and then guessed the next proverb"
      (-> (session app)
          (#(binding [*all-proverbs* ["bar"]] (request % "/")))
          (#(binding [*all-proverbs* ["foo"]]
             (reduce (fn [state _] (request state "/" :request-method :post, :params {:guess "hmm"}))
                     % (range (dec proverbs/initial-tries)))))
          (#(do (is (= ["bar"] (get-in % [:response :body :icons]))) %))
          (#(binding [*all-proverbs* ["foo"]] (request % "/" :request-method :post, :params {:guess "hmm"})))
          (#(do (is (= [{:url "foo"}] (get-in % [:response :body :icons])))
                (is (zero? (get-in % [:response :body :score :points]))) %))
          (#(binding [*all-proverbs* ["bar"]] (request % "/" :request-method :post, :params {:guess "Foo."})))
          (#(do (is (= [{:url "bar"}] (get-in % [:response :body :icons])))
                (is (pos? (get-in % [:response :body :score :points])))))))
    (testing "POST returns 'it was' and last guess"
      (-> (session app)
          (#(binding [*all-proverbs* ["bar"]] (request % "/")))
          (#(reduce (fn [state _] (request state "/" :request-method :post, :params {:guess "hmm"}))
                    % (range (dec proverbs/initial-tries))))
          (#(do (is (not (get-in % [:response :body :it-was])))
                (is (not (get-in % [:response :body :you-typed])))
                (is (= "hmm" (get-in % [:response :body :guess]))) %))
          (#(binding [*all-proverbs* ["foo"]] (request % "/" :request-method :post, :params {:guess "hmm"})))
          (#(do (is (= "bar" (get-in % [:response :body :it-was])))
                (is (= "hmm" (get-in % [:response :body :you-typed])))
                (is (not (get-in % [:response :body :guess]))) %))
          (request "/" :request-method :post, :params {:guess "foo"})
          (#(do (is (not (get-in % [:response :body :it-was])))
                (is (not (get-in % [:response :body :you-typed])))
                (is (not (get-in % [:response :body :guess])))))))
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
                                    (#(do (is (not (get-in % [:response :body :praise]))) %))))
                  (initial-request) (range proverbs/initial-tries)))))
    (testing "not-found route"
      (let [response (-> (session app) (request "/invalid") :response)]
        (is (= 404 (:status response)))))))

(def app
  (wrap-defaults app-routes
                 (assoc-in site-defaults [:security :anti-forgery] false)))
