(ns noun-quiz.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [noun-quiz.handler :refer :all]))

(deftest test-app
  (testing "main route"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 200))
      (is (.contains (:body response) "proverb"))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))
