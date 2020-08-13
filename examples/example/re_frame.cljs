(ns example.re-frame
  "
  Like re-frame API but with the convention that handlers pass state through and
  use :fx key to trigger fx.

  (reg-event-fx ::event1 (fn [ctx] (assoc-in ctx [:db :loading?] true)))
  (reg-event-fx ::event2 (fn [ctx] (update ctx :fx conj {:dispatch [::x]})))
  "
  (:require [condense.event-loop :as el]
            [reagent.core :as r]))

(def app-db (r/atom {}))
(def std-ins [[:db] [:fx] [:event]])
(defn reg-cofx [id f] (el/reg {:id id :input f}))
(defn reg-event-fx
  ([id f] (el/reg {:id id :ins std-ins :logic (fn [{:keys [event] :as ctx}] (f ctx event))}))
  ([id ins f] (el/reg {:id id :ins (into std-ins ins) :logic (fn [{:keys [event] :as ctx}] (f ctx event))})))
(defn reg-fx [id f] (el/reg {:id id :action f}))
(def dispatch el/dispatch)

(el/reg {:id :db :input #(deref app-db) :transition #(reset! app-db %2)})
(el/reg {:id :fx :input (constantly []) :transition el/do-actions})
(el/reg {:id :event :input :event})
(el/reg {:id :args :input second})
(el/reg {:id :dispatch :action dispatch})