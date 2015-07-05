(ns noun-quiz.view
  (:require [garden.core :refer [css]]
            [garden.units :refer [px]]
            [clojure.test :refer [with-test testing is]])
  (:use [hiccup page element form]))

(defn layout [{:keys [title description style header footer]} & content]
  (html5
    [:head
     [:title title]
     [:meta {:name "description", :content description}]
     [:link {:href "http://fonts.googleapis.com/css?family=Source+Sans+Pro:300,400,600"
             :rel  "stylesheet", :type "text/css"}]
     [:style {:type "text/css"}
      (css
        [:body :input {:font-family "'Source Sans Pro', sans-serif", :font-size (px 100)
                       :font-weight 400, :text-align "center"}]
        [:body {:margin 0, :padding 0}]
        [:#content {:margin :auto, :position :absolute, :top 0, :left 0, :bottom 0, :right 0}]
        [:#footer {:position :absolute, :bottom 0, :width "100%"}]
        style)]
     ]
    [:body [:div#header header] [:div#content content] [:div#footer footer]]))

(with-test
  (defn challenge [{:keys [score icons]}]
    (layout {:style  [[:#content {:height (px 356)}]
                      [:.clue
                       [:img {:width (px 100), :height (px 100)}]
                       [:span {:font-weight 600}]
                       [:img :span {:margin-left (px 15), :margin-right (px 15)}]]
                      [:.guess {:margin-bottom (px 8)}
                       [:input {:border :none, :font-size (px 50), :width "99%"}
                        [:&:focus {:outline :none}]]]
                      [:button {:padding 0, :border :none, :background :none, :font-size (px 50)}]
                      [:#header :#footer {:font-size        (px 20), :font-weight 300,
                                          :padding-top      (px 5), :padding-bottom (px 5)
                                          :background-color :black, :color :white, :opacity 0.34}]
                      [:#header
                       [:span {:font-weight 400}]]
                      [:#footer
                       [:img {:width          (px 20), :height (px 20), :margin-right (px 10)
                              :-webkit-filter "invert(1)", :filter "invert(1)"}]
                       [:span {:margin-left (px 10), :margin-right (px 10)}]]]
             :header (list "You have " [:span (:points score)] " points after guessing "
                           [:span (:guessed score)] " and missing " [:span (:missed score)]
                           " proverbs. You have " [:span (:tries score)] " tries for this proverb.")
             :footer (->> icons
                          (filter map?)
                          (map #(-> [:span (image (:url %)) (format "by %s from The Noun Project" (:by %))])))}
            [:div.clue (map #(if (map? %)
                              (image (:url %))
                              [:span %])
                            icons)]
            (form-to [:post "/"]
                     [:div.guess (text-field {:placeholder "type the above proverb"
                                              :autofocus   true, :autocomplete :off} "guess")]
                     [:button {:type :submit} "➔"])))
  (testing "renders icons and credits"
    (let [contains-subcoll (fn [coll subcoll]
                             (some #{subcoll} (tree-seq sequential? identity coll)))
          data {:icons ["1" {:url "foo" :by "fooby"} "2" {:url "bar" :by "barby"}]}]
      (with-redefs [layout (fn [{:keys [header footer]} & content] (list header content footer))]
        (is (contains-subcoll (challenge data) (list [:span "1"] (image "foo") [:span "2"] (image "bar"))))
        (is (contains-subcoll (challenge data) (list [:span (image "foo") "by fooby from The Noun Project"]
                                                     [:span (image "bar") "by barby from The Noun Project"])))))))
