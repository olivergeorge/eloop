(ns example.composing
  "
  Demonstrates an app which composes handlers out of simple app logic functions.
  "
  (:require [condense.event-loop :refer [cfg reg do-actions dispatch]]
            [clojure.data :as data]
            [reagent.core :as r]))

; Basics
(def app-db (r/atom {}))
(cfg :std-ins [[:db] [:fx] [:event]])
(reg {:id :db :input #(deref app-db) :transition #(reset! app-db %2)})
(reg {:id :fx :transition do-actions})
(reg {:id :event :input :event})
(reg {:id :dispatch :action dispatch})
(reg {:id :args :input second})

; App logic
(defn log-state [s] (println ::log.state s) s)
(defn set-loading [s] (assoc-in s [:db :loading?] true))
(defn clear-loading [s] (update s :db dissoc :loading?))
(defn get-data [s] (update s :fx conj {::GET {:url "/endpoint/data" :cb #(dispatch [::get-resp %])}}))
(defn get-resp [s] (assoc-in s [:db :data] (get-in s [:event 1])))
(defn GET [{:keys [cb]}] (js/setTimeout #(cb {:results [1 2 3]}) 1000))

; Composing handlers
(reg {:id ::bootstrap :logic (comp set-loading get-data log-state)})
(reg {:id ::get-resp :logic (comp get-resp clear-loading log-state)})
(reg {:id ::GET :action GET})

;; Debug
(defn diff-report [k [a b]] (println k) (println :only-before a) (println :only-after b))
(add-watch app-db ::app-db (fn [k _ o n] (diff-report k (data/diff o n))))

(comment (dispatch [::bootstrap]))
