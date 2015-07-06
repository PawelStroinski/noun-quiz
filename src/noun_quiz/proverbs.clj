(ns noun-quiz.proverbs
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [oauth.v1 :refer [oauth-client]]
            [robert.bruce :refer [try-try-again]]
            [clojure.string :as str]
            [clojure.set :refer [intersection difference]]
            [clojure.test :refer [with-test testing is]]))

(with-test
  (defn read-proverbs []
    (-> "proverbs.json" io/resource io/file slurp json/read-str
        (get "proverbs") ((partial map vals)) flatten))
  (testing "has to return lots of strings"
    (is (> (count (read-proverbs)) 100))
    (is (every? (partial instance? String) (read-proverbs)))))

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

(with-test
  (defn fetch-icon [word {:keys [try-options] :as config}]
    (try-try-again try-options #(try (-fetch-icon word config)
                                     (catch Exception e
                                       (when-not (= (get-in (ex-data e) [:object :status]) 404)
                                         (throw e))))))
  (let [config {:thenounproject {:key "foo", :secret "bar"}, :try-options {:tries 1}}]
    (testing "when icons found, returns one random"
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
    (testing "when no icon found, is graceful"
      (with-redefs [oauth-client (fn [& args] (throw (ex-info "Oops" {:object {:status 404}})))]
        (is (nil? (fetch-icon "monitor" config)))))
    (testing "when some other error, throws"
      (with-redefs [oauth-client (fn [& args] (throw (Exception. "Oops")))]
        (is (thrown? Exception (fetch-icon "monitor" config)))))))

(defn- split-into-words [text]
  (->> (re-seq #"(?:\W+|\w+)" text)
       (map str/trim)
       (filter (partial not= ""))
       (#(if (= (last %) ".") (butlast %) %))))

(with-test
  (defn icons [proverb {:keys [always-as-text] :as config} fetcher]
    (let [proverb-words (split-into-words proverb)
          in-always-as-text (fn [word] (some #(= (str/upper-case word)
                                                 (str/upper-case %)) always-as-text))
          not-a-word (fn [word] (not (re-find #"\w" word)))]
      (pmap (fn [word] (if (or (in-always-as-text word) (not-a-word word))
                         word
                         (or (fetcher word config) word)))
            proverb-words)))
  (testing "words in always-as-text or not found by fetcher or commas etc. are returned as-is, the rest as icons"
    (let [config {:always-as-text ["thisalwaysastext1" "thisalwaysastext2" "s"]}
          fetcher (fn [word & _] (when-not (= word "thishasnoicon") {:icon-for word}))]
      (is (= ["thisalwaysastext1" {:icon-for "foo"} "thishasnoicon" "Thisalwaysastext2" {:icon-for "bar"}]
             (icons "thisalwaysastext1 foo thishasnoicon Thisalwaysastext2 bar." config fetcher)))
      (is (= ["thisalwaysastext1" "'" "s" {:icon-for "foo"} "'" "s" "(" "thishasnoicon" "," "Thisalwaysastext2" "."
              {:icon-for "bar"} ")?"]
             (icons "thisalwaysastext1's foo's  (thishasnoicon, Thisalwaysastext2. bar)?" config fetcher))))))

(with-test
  (defn reveal-guessed-words [proverb guess icons]
    (let [proverb-words (split-into-words proverb)
          guess-words (->> guess split-into-words (map str/upper-case) set)]
      (map (fn [icon word]
             (if (-> word str/upper-case guess-words) word icon))
           icons proverb-words)))
  (testing "returns icons"
    (let [icons [{:url "some"} "foo" {:url "proverb"}]]
      (doseq [[expected guess] (list [icons ""]
                                     [["some" "foo" {:url "proverb"}] "hmm some"]
                                     [["some" "foo" "proverb"] "hmm proverb hmm SOME"])]
        (is (= expected (reveal-guessed-words "some foo proverb" guess icons)))))))

(with-test
  (defn reveal-one-word [proverb icons]
    (let [proverb-words (split-into-words proverb)
          reveal (->> icons (filter map?) seq rand-nth)]
      (map (fn [icon word]
             (if (= reveal icon) word icon))
           icons proverb-words)))
  (let [proverb "some foo proverb"
        icons [{:url "some"} "foo" {:url "proverb"}]]
    (testing "reveals random word"
      (doseq [expected (list [{:url "some"} "foo" "proverb"] ["some" "foo" {:url "proverb"}])]
        (is (some #{expected} (repeatedly 10 #(reveal-one-word proverb icons))))))
    (testing "reveals unrevealed word"
      (doseq [icons (list [{:url "some"} "foo" "proverb"] ["some" "foo" "proverb"])]
        (is (= ["some" "foo" "proverb"] (reveal-one-word proverb icons)))))))

(with-test
  (defn guessed? [proverb guess]
    (let [f #(->> (split-into-words %)
                  (filter (fn [word] (re-find #"\w" word)))
                  (map str/upper-case))]
      (= (f proverb) (f guess))))
  (testing "all words in the right order have to be in the guess"
    (is (guessed? "some proverb" " some  proverb "))
    (is (guessed? "some-proverb" "some - proverb"))
    (is (guessed? "Some proverb" "some Proverb"))
    (is (guessed? "some, proverb" "some proverb!"))
    (is (not (guessed? "some proverb" "someproverb")))))

(def initial-tries 3)
(def initial-score {:points 0, :guessed 0, :missed 0, :tries initial-tries})

(with-test
  (defn update-score [{:keys [tries] :as score} guessed]
    {:pre [(> tries 0)]}
    (let [last-try (= 1 tries)]
      (if guessed
        (-> score
            (update-in [:points] + 3)
            (update-in [:guessed] inc)
            (assoc :tries initial-tries))
        (-> score
            (update-in [:points] (if last-try #(max 0 (dec %)) identity))
            (update-in [:missed] (if last-try inc identity))
            (update-in [:tries] (if last-try (constantly initial-tries) dec))))))
  (let [score {:points 2, :guessed 2, :missed 4, :tries 3}]
    (testing "when guessed"
      (is (= {:points 5, :guessed 3, :missed 4, :tries initial-tries} (update-score score true))))
    (testing "when not guessed"
      (is (= {:points 2, :guessed 2, :missed 4, :tries 2} (update-score score false)))
      (is (= {:points 2, :guessed 2, :missed 4, :tries 1} (update-score (assoc score :tries 2) false)))
      (is (= {:points 1, :guessed 2, :missed 5, :tries initial-tries} (update-score (assoc score :tries 1) false)))
      (is (= {:points 0, :guessed 2, :missed 5, :tries initial-tries} (update-score (assoc score :points 0
                                                                                                 :tries 1) false))))))
