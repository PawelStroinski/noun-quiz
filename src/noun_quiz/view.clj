(ns noun-quiz.view
  (:require [garden.core :refer [css]]
            [garden.units :refer [px]]
            [clojure.test :refer [with-test testing is]]
            [clojure.string :as str])
  (:use [hiccup page element form]))

(defn- contains-subcoll [coll subcoll]
  (some #{subcoll} (tree-seq sequential? identity coll)))

(defn- echo-layout [{:keys [header footer]} & content]
  (list header content footer))

(defn layout [{:keys [title description style header footer]} & content]
  (html5
    [:head
     [:title title]
     [:meta {:name "description", :content description}]
     [:link {:href "http://fonts.googleapis.com/css?family=Source+Sans+Pro:300,400,600"
             :rel  "stylesheet", :type "text/css"}]
     [:style {:type "text/css"}
      (css
        [:body :input :button {:font-family "'Source Sans Pro', sans-serif", :font-size (px 100)
                               :font-weight 400, :text-align "center"}]
        [:body {:margin 0, :padding 0}]
        [:.inputs {:margin-bottom (px 8)}
         [:input {:border :none, :font-size (px 50), :width "99%"}
          [:&:focus {:outline :none}]]]
        [:button {:padding 0, :border :none, :background :none, :font-size (px 50)}]
        [:#content {:margin :auto, :position :absolute, :top 0, :left 0, :bottom 0, :right 0}]
        [:#footer {:position :absolute, :bottom 0, :width "100%"}]
        style)]
     ]
    [:body [:div#header header] [:div#content content] [:div#footer footer]]))

(def ^:private colors {:initial :black, :first-retry :navy, :second-retry :blue, :success :green, :failure :red})

(with-test
  (defn challenge [{:keys [score icons it-was you-typed praise email]}]
    (layout {:title       "Guess the Proverb"
             :description "The game: type the proverb by looking at icons representing words in the proverb."
             :style       [[:#content {:height (px 356)}]
                           [:.clue
                            [:img {:width (px 100), :height (px 100)}]
                            [:span {:font-weight 600}]
                            [:img :span {:margin-left (px 5), :margin-right (px 5)}]]
                           [:#header :#footer {:padding-top      (px 5), :padding-bottom (px 5)
                                               :background-color :black, :opacity 0.34}
                            [:& :a :button {:font-size (px 20), :font-weight 300, :color :white}]]
                           [:#header {:background-color (colors (cond
                                                                  (= 2 (:tries score)) :first-retry
                                                                  (= 1 (:tries score)) :second-retry
                                                                  praise :success
                                                                  it-was :failure
                                                                  :else :initial))}]
                           [:#header
                            [:span {:font-weight 400}]
                            [:a :button {:opacity 0.8, :text-decoration :none, :cursor :pointer}]
                            [:form {:display :inline}]]
                           [:#footer
                            [:img {:width          (px 20), :height (px 20), :margin-right (px 10)
                                   :-webkit-filter "invert(1)", :filter "invert(1)"}]
                            [:span {:margin-left (px 10), :margin-right (px 10)}]]]
             :header      (list (when it-was [:div "It was " [:span it-was]])
                                (when-not (str/blank? you-typed) [:div "You typed " [:span you-typed]])
                                (when praise [:div praise])
                                "You have " [:span (:points score)] " points after guessing "
                                [:span (:guessed score)] " and missing " [:span (:missed score)]
                                " proverbs. You have " [:span (:tries score)] " tries left for this proverb. "
                                (if email
                                  (list "Logged in: " email " "
                                        (form-to [:post "/logout"] [:button {:type :submit} "Log out"]))
                                  (link-to "/login" "Log in or Register")))
             :footer      (->> icons
                               (filter map?)
                               (map #(-> [:span (image (:url %)) (format "by %s from The Noun Project" (:by %))])))}
            [:div.clue (interpose " " (map #(if (map? %)
                                             (image (:url %))
                                             [:span %])
                                           icons))]
            (form-to [:post "/"]
                     [:div.inputs (text-field {:placeholder "type the above proverb"
                                               :autofocus   true, :autocomplete :off} "guess")]
                     [:button {:type :submit} "➔"])))
  (with-redefs [layout echo-layout]
    (testing "renders icons and credits"
      (let [data {:icons ["1" {:url "foo" :by "fooby"} "2" {:url "bar" :by "barby"}]}]
        (is (contains-subcoll (challenge data) (list [:span "1"] " " (image "foo") " " [:span "2"] " " (image "bar"))))
        (is (contains-subcoll (challenge data) (list [:span (image "foo") "by fooby from The Noun Project"]
                                                     [:span (image "bar") "by barby from The Noun Project"])))))
    (testing "renders 'it was'"
      (is (contains-subcoll (challenge {:it-was "Foo"}) [:span "Foo"]))
      (is (contains-subcoll (challenge {:you-typed "Bar"}) [:span "Bar"]))
      (is (not (contains-subcoll (challenge nil) [:div "It was " [:span nil]])))
      (is (not (contains-subcoll (challenge {:you-typed "  "}) [:span "  "]))))
    (testing "renders praise"
      (is (contains-subcoll (challenge {:praise "Wow!"}) [:div "Wow!"]))
      (is (not (contains-subcoll (challenge nil) [:div nil])))))
  (testing "renders log in link or email & log out link"
    (is (.contains (challenge nil) "/login"))
    (is (not (.contains (challenge nil) "/logout")))
    (is (.contains (challenge {:email "foo"}) "foo"))
    (is (.contains (challenge {:email "foo"}) "/logout"))
    (is (not (.contains (challenge {:email "foo"}) "/login"))))
  (testing "renders different header colors"
    (with-redefs [layout (fn [{:keys [style]} & _] style)]
      (is (contains-subcoll (challenge nil) [:#header {:background-color (colors :initial)}]))
      (is (contains-subcoll (challenge {:score {:tries 2}}) [:#header {:background-color (colors :first-retry)}]))
      (is (contains-subcoll (challenge {:score {:tries 1}}) [:#header {:background-color (colors :second-retry)}]))
      (is (contains-subcoll (challenge {:praise "Wow!"}) [:#header {:background-color (colors :success)}]))
      (is (contains-subcoll (challenge {:it-was "Foo"}) [:#header {:background-color (colors :failure)}])))))

(with-test
  (defn login-form [{:keys [wrong-password]}]
    (layout {:title       "Log in or Register"
             :description "Guess the Proverb - log in to the game"
             :style       [[:#content {:height (px 415)}]
                           [:#header {:font-size (px 30)}]]
             :header      (when wrong-password [:div "Wrong password."])}
            (form-to [:post "/login"]
                     [:div.inputs (text-field {:placeholder "email", :autofocus true
                                               :required    true, :type :email} "email")
                      (password-field {:placeholder "password", :required true} "password")]
                     [:button {:type :submit} "➔"])))
  (with-redefs [layout echo-layout]
    (testing "renders 'wrong password'"
      (is (contains-subcoll (login-form {:wrong-password true}) [:div "Wrong password."]))
      (is (not (contains-subcoll (login-form nil) [:div "Wrong password."]))))))
