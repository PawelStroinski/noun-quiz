(ns noun-quiz.view-test
  (:require [clojure.test :refer :all]
            [noun-quiz.view :refer :all]
            [hiccup.element :refer :all]))

(defn contains-subcoll [coll subcoll]
  (some #{subcoll} (tree-seq sequential? identity coll)))

(deftest challenge-test
  (let [data {:icons ["1" {:url "foo" :by "fooby"} "2" {:url "bar" :by "barby"}]}]
    (with-redefs [layout (fn [{:keys [header footer]} & content] (list header content footer))]
      (is (contains-subcoll (challenge data) (list [:span "1"] (image "foo") [:span "2"] (image "bar"))))
      (is (contains-subcoll (challenge data) (list [:span (image "foo") "by fooby from The Noun Project"]
                                                   [:span (image "bar") "by barby from The Noun Project"]))))))
