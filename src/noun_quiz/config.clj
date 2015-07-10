(ns noun-quiz.config
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defn read-config []
  (merge
    (-> "config.edn" io/resource io/file slurp edn/read-string)
    (if-let [env-var (System/getenv "NOUN_QUIZ_CONFIG")] (edn/read-string env-var))))
