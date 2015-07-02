(ns noun-quiz.proverbs-test
  (:require [clojure.test :refer :all]
            [noun-quiz.proverbs :refer :all]
            [oauth.v1 :refer [oauth-client]]
            [clojure.java.io :as io]))

(deftest read-proverbs-test
  (is (> (count (read-proverbs)) 100))
  (is (every? (partial instance? String) (read-proverbs))))

(deftest fetch-icon-test
  (let [config {:thenounproject {:key "foo", :secret "bar"}, :try-options {:tries 1}}]
    (testing "success"
      (with-redefs [oauth-client (fn [& args]
                                   (is (= ["foo" "bar" "" ""] args))
                                   (fn [req] (is (= {:method :get
                                                     :url    "http://api.thenounproject.com/icons/monitor"} req))
                                     (-> "nounproj-test-resp.edn" io/resource io/file slurp read-string)))]
        (let [icons (repeatedly 50 #(fetch-icon "monitor" config))]
          (is (every? (partial re-matches #"https.*200\.png") (map :url icons)))
          (is (> (-> (map :url icons) distinct count) 10))
          (is (empty? (filter #{"https://license?/816-200.png"} (map :url icons))))
          (is (some #{"Claudio Gomboli" "Wilson Joseph" "Simple Icons" "Maurizio Fusillo"} (map :by icons))))))
    (testing "not found"
      (with-redefs [oauth-client (fn [& args] (throw (ex-info "Oops" {:object {:status 404}})))]
        (is (nil? (fetch-icon "monitor" config)))))
    (testing "other error"
      (with-redefs [oauth-client (fn [& args] (throw (Exception. "Oops")))]
        (is (thrown? Exception (fetch-icon "monitor" config)))))))

(deftest icons-test
  (let [config {:always-as-text ["thisalwaysastext1" "thisalwaysastext2" "s"]}
        fetcher (fn [word & _] (when-not (= word "thishasnoicon") {:icon-for word}))]
    (is (= ["thisalwaysastext1" {:icon-for "foo"} "thishasnoicon" "Thisalwaysastext2" {:icon-for "bar"}]
           (icons "thisalwaysastext1 foo thishasnoicon Thisalwaysastext2 bar." config fetcher)))
    (is (= ["thisalwaysastext1" "'" "s" {:icon-for "foo"} "'" "s" "(" "thishasnoicon" "," "Thisalwaysastext2" "."
            {:icon-for "bar"} ")?"]
           (icons "thisalwaysastext1's foo's  (thishasnoicon, Thisalwaysastext2. bar)?" config fetcher)))))
