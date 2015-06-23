(ns noun-quiz.view
  (:require [garden.core :refer [css]]
            [garden.units :refer [px]]
            [ring.util.anti-forgery :as af])
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

(defn challenge []
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
           :header (list "You have " [:span 0] " points after guessing " [:span 0] " and missing " [:span 0] " proverbs. You have " [:span 3] " tries for this proverb.")
           :footer (list [:span (image "1.png") "by Boudewijn Mijnlieff from The Noun Project"] [:span (image "2.png") "by Evan Shuster from The Noun Project"])}
          [:div.clue
           (image "1.png")
           [:span "a"]
           (image "2.png")
           [:span "a"]
           (image "2.png")]
          (form-to [:post "/"]
                   [:div.guess (text-field {:placeholder "type the above proverb"
                                            :autofocus   true, :autocomplete :off} "guess")]
                   [:button {:type :submit} "➔"]
                   (af/anti-forgery-field))))
