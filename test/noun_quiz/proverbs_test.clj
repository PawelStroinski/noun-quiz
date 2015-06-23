(ns noun-quiz.proverbs-test
  (:require [clojure.test :refer :all]
            [noun-quiz.proverbs :refer :all]))

(deftest read-proverbs-test
  (is (> (count (read-proverbs)) 100))
  (is (every? (partial instance? String) (read-proverbs))))
