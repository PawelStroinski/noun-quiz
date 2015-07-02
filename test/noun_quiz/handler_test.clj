(ns noun-quiz.handler-test
  (:require [clojure.test :refer :all]
            [peridot.core :refer :all]
            [ring.util.response :as resp]
            [noun-quiz.handler :refer :all]
            [noun-quiz.proverbs :as proverbs]
            [noun-quiz.view :as view]))

(deftest test-app
  (testing "main route"
    (let [all-proverbs ["Foo bar" "Bar foo"]]
      (with-redefs [proverbs/read-proverbs (fn [] all-proverbs)
                    proverbs/icons (fn [proverb {:keys [always-as-text] :as config} fetcher]
                                     (is (> (count always-as-text) 10))
                                     (is (every? (partial instance? String) always-as-text))
                                     (is (= fetcher proverbs/fetch-icon))
                                     [{:icons-for proverb}])
                    view/challenge (fn [data] (resp/response data))]
        (let [response #(-> (session app) (request "/") :response)
              response-data (comp :body response)]
          (is (= (:status (response)) 200))
          (is (= (:score (response-data)) {:points 0, :guessed 0, :missed 0, :tries 3}))
          (doseq [proverb all-proverbs]
            (is (some #(= (:icons %) [{:icons-for proverb}]) (repeatedly 10 response-data))))))))

  (testing "not-found route"
    (let [response (-> (session app) (request "/invalid") :response)]
      (is (= (:status response) 404)))))
