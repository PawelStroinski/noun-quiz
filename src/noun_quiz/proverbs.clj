(ns noun-quiz.proverbs
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [oauth.v1 :refer [oauth-client]]
            [robert.bruce :refer [try-try-again]]
            [clojure.string :as str]))

(defn read-proverbs []
  (-> "proverbs.json" io/resource io/file slurp json/read-str
      (get "proverbs") ((partial map vals)) flatten))

(defn- -fetch-icon [word config]
  (let [req {:method :get, :url (str "http://api.thenounproject.com/icons/" word)}
        cfg (:thenounproject config)
        client (oauth-client (:key cfg) (:secret cfg) "" "")
        icon (->> req
                  client
                  :icons
                  (filter #(#{"creative-commons-attribution" "public-domain"}
                            (:license-description %)))
                  rand-nth)]
    {:url (:preview-url icon) :by (get-in icon [:uploader :name])}))

(defn fetch-icon [word {:keys [try-options] :as config}]
  (try-try-again try-options #(try (-fetch-icon word config)
                                   (catch Exception e
                                     (when-not (= (get-in (ex-data e) [:object :status]) 404)
                                       (throw e))))))

(defn icons [proverb {:keys [always-as-text] :as config} fetcher]
  (let [words (str/split proverb #"\W+")])
    (pmap (fn [word] (if (some #(= (str/upper-case word) (str/upper-case %)) always-as-text)
                        word
                        (fetcher word config)))
          words))
