(ns noun-quiz.users
  (:require [clojurewerkz.scrypt.core :as sc]
            [korma.db :refer [defdb postgres]]
            [korma.core :refer [defentity select where insert values update set-fields]]
            [noun-quiz.config :refer [read-config]]))

(defdb db (-> (read-config) :database postgres))

(defentity users)

(defn login-or-register [email password]
  (if-let [user (-> (select users (where {:email email})) first)]
    (when (sc/verify password (:password_hash user)) user)
    (insert users (values {:email email :password_hash (sc/encrypt password 16384 8 1)}))))

(defn save-score [email score]
  (update users
          (set-fields (dissoc score :tries))
          (where {:email email})))
