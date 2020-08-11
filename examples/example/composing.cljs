(ns example.composing
  "
  Demonstrates an app which composes handlers out of simple app logic functions.
  "
  (:require [condense.event-loop :refer [reg fx-state fx-transition current-event dispatch log]]
            [clojure.data :as data]
            [reagent.core :as r]))

; Basics
(def app-db (r/atom {}))
(reg {:id :db :kind :fsm :state #(assoc % :db @app-db) :transition #(reset! app-db %)})
(reg {:id :fx :kind :fsm :state fx-state :transition fx-transition})
(reg {:id :event :kind :input :f (fn [s] (assoc s :event current-event))})
(reg {:id :args :kind :input :f (fn [s m] (update s :args merge m))})
(reg {:id :dispatch :kind :action :f dispatch})

; App logic
(defn log-state [s] (println ::log.state s) s)
(defn set-loading [s] (assoc-in s [:db :loading?] true))
(defn clear-loading [s] (update s :db dissoc :loading?))
(defn get-data [s] (update s :fx conj {::GET {:url "/endpoint/data" :cb #(dispatch [::get-resp %])}}))
(defn get-resp [s] (assoc-in s [:db :data] (get-in s [:event 1])))
(defn GET [{:keys [cb]}] (js/setTimeout #(cb {:results [1 2 3]}) 1000))

; Composing handlers
(reg {:kind :event :id ::bootstrap :f (comp set-loading get-data log-state)})
(reg {:kind :event :id ::get-resp :f (comp get-resp clear-loading log-state)})
(reg {:kind :action :id ::GET :f GET})

;; Debug
(defn diff-report [k [a b]] (log k) (log :only-before a) (log :only-after b))
(add-watch app-db ::app-db (fn [k _ o n] (diff-report k (data/diff o n))))

(comment (dispatch [::bootstrap]))
