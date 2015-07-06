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

(defn- response [body]
  (-> (resp/response body) (resp/content-type "text/html") (resp/charset "utf-8")))

(defn- assemble-response [proverb icons score]
  (-> (view/challenge {:score score, :icons icons})
      (response)
      (assoc-in [:session :proverb] proverb)
      (assoc-in [:session :icons] icons)
      (assoc-in [:session :score] score)))

(with-test
  (defroutes app-routes
             (GET "/" {{:keys [proverb icons score]} :session}
               (let [proverb (or proverb (-> (proverbs/read-proverbs) rand-nth))
                     icons (or icons (proverbs/icons proverb (read-config) proverbs/fetch-icon))
                     score (or score proverbs/initial-score)]
                 (assemble-response proverb icons score)))
             (POST "/" {{:keys [guess]}               :params
                        {:keys [proverb icons score]} :session}
               (let [icons (->> icons
                                (proverbs/reveal-guessed-words proverb guess)
                                (proverbs/reveal-one-word proverb))
                     score (assoc score :tries 2)]
                 (assemble-response proverb icons score)))
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
        (is (= {:points 0, :guessed 0, :missed 0, :tries 3} (:score (response-data))))
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
          (is (= {:points 0, :guessed 0, :missed 0, :tries 2} (:score (response-data ""))))
          (is (= ["revealed-one-word"] (:icons (response-data "hmm")))))))
    (testing "GET doesn't each time generate new proverb or icons or score"
      (-> (session app)
          (#(binding [*all-proverbs* ["FOO"]] (request % "/")))
          (request "/" :request-method :post, :params {:guess ""})
          (request "/")
          (#(do (is (= ["FOO"] (get-in % [:response :body :icons])))
                (is (= 2 (get-in % [:response :body :score :tries])))))))
    (testing "not-found route"
      (let [response (-> (session app) (request "/invalid") :response)]
        (is (= 404 (:status response)))))))

(def app
  (wrap-defaults app-routes
                 (assoc-in site-defaults [:security :anti-forgery] false)))
