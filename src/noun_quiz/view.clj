(ns noun-quiz.view
  (:require [garden.core :refer [css]]
            [garden.units :refer [px]]
            [ring.util.anti-forgery :as af])
  (:use [hiccup page element form]))

(defn layout [{:keys [title description style]} & content]
  (html5
    [:head
     [:title title]
     [:meta {:name "description", :content description}]
     [:link {:href "http://fonts.googleapis.com/css?family=Source+Sans+Pro:400,600"
             :rel  "stylesheet", :type "text/css"}]
     [:style {:type "text/css"}
      (css
        [:body :input {:font-family "'Source Sans Pro', sans-serif", :font-size (px 100)
                       :font-weight 400, :text-align "center"}]
        [:body {:margin 0, :padding 0}]
        [:.wrapper {:margin :auto, :position :absolute, :top 0, :left 0, :bottom 0, :right 0}]
        style)]
     ]
    [:body [:div.wrapper content]]))

(defn challenge []
  (layout {:style
           [[:.wrapper {:height (px 356)}]
            [:.clue {:font-weight 600}
             [:img {:width (px 100), :height (px 100)}]
             [:img :span {:margin-left (px 15), :margin-right (px 15)}]]
            [:.guess {:margin-bottom (px 8)}
             [:input {:border :none, :font-size (px 50), :width "99%"}
              [:&:focus {:outline :none}]]]
            [:button {:padding 0, :border :none, :background :none, :font-size (px 50)}]]}
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
