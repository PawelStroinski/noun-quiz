(ns noun-quiz.handler
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [noun-quiz.view :refer :all]))

(defn config []
  (-> "config.edn" io/resource io/file slurp edn/read-string))

(defroutes app-routes
           (GET "/" [] (challenge))
           (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
