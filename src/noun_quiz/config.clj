(ns noun-quiz.config
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defn read-config []
  (-> "config.edn" io/resource io/file slurp edn/read-string))
