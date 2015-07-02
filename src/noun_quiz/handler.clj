(ns noun-quiz.handler
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [noun-quiz.proverbs :as proverbs]
            [noun-quiz.view :as view]))

(defn read-config []
  (-> "config.edn" io/resource io/file slurp edn/read-string))

(defroutes app-routes
           (GET "/" {session :session}
             (view/challenge {:score {:points 0, :guessed 0, :missed 0, :tries 3},
                              :icons (-> (proverbs/read-proverbs)
                                         rand-nth
                                         (proverbs/icons (read-config) proverbs/fetch-icon))}))
           (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
