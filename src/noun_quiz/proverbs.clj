(ns noun-quiz.proverbs
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]))

(defn read-proverbs []
   (-> "proverbs.json" io/resource io/file slurp json/read-str
       (get "proverbs") ((partial map vals)) flatten))
