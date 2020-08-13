(ns example.composing
  "
  Demonstrates an app which composes handlers out of simple app logic functions.
  "
  (:require [condense.event-loop :refer [reg do-actions dispatch log]]
            [clojure.data :as data]
            [reagent.core :as r]))

; Basics
(def app-db (r/atom {}))
(def std-ins [[:db] [:fx] [:event]])
(reg {:id :db :input #(deref app-db) :transition #(reset! app-db %2)})
(reg {:id :fx :input (constantly []) :transition do-actions})
(reg {:id :event :input :event})
(reg {:id :args :input second})
(reg {:id :dispatch :action dispatch})

; App logic
(defn log-state [s] (log ::log.state s) s)
(defn set-loading [s] (assoc-in s [:db :loading?] true))
(defn clear-loading [s] (update s :db dissoc :loading?))
(defn get-data [s] (update s :fx conj {::GET {:url "/endpoint/data" :cb #(dispatch [::get-resp %])}}))
(defn get-resp [s] (assoc-in s [:db :data] (get-in s [:event 1])))
(defn GET [{:keys [cb]}] (js/setTimeout #(cb {:results [1 2 3]}) 1000))

; Composing handlers
(reg {:id ::bootstrap :ins std-ins :logic (comp set-loading get-data log-state)})
(reg {:id ::get-resp :ins std-ins :logic (comp get-resp clear-loading log-state)})
(reg {:id ::GET :action GET})

;; Debug
(defn diff-report [k [a b]] (log k) (log :only-before a) (log :only-after b))
(add-watch app-db ::app-db (fn [k _ o n] (diff-report k (data/diff o n))))

(comment (dispatch [::bootstrap]))
