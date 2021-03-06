(defproject noun-quiz "0.1.0-SNAPSHOT"
            :description "FIXME: write description"
            :url "http://example.com/FIXME"
            :min-lein-version "2.0.0"
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [compojure "1.3.1"]
                           [ring/ring-defaults "0.1.2"]
                           [hiccup "1.0.5"]
                           [garden "1.2.5"]
                           [oauth-clj "0.1.13"]
                           [org.clojure/data.json "0.2.6"]
                           [robert/bruce "0.7.1"]
                           [clojurewerkz/scrypt "1.2.0"]
                           [korma "0.4.2"]
                           [org.postgresql/postgresql "9.4-1201-jdbc4"]
                           [log4j "1.2.17"]
                           [peridot "0.4.0"]
                           [ring/ring-servlet "1.4.0"]]
            :plugins [[lein-ring "0.8.13"]]
            :ring {:handler noun-quiz.handler/app}
            :test-paths ["src"]
            :profiles
            {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]]}})
