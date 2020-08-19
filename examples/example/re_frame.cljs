(ns example.re-frame
  "
  Like re-frame API but...
   1. by convention that handlers pass state through and use :fx key for effects.
   2. no interceptors but :ins allows for cofx style input data to be passed to event handlers.

  (reg-event-fx ::event1 (fn [ctx] (assoc-in ctx [:db :loading?] true)))
  (reg-event-fx ::event2 (fn [ctx] (update ctx :fx conj {:dispatch [::x]})))
  "
  (:require [condense.event-loop :as el]
            [reagent.core :as r]))

(def app-db (r/atom {}))
(defn reg-cofx [id f] (el/reg {:id id :input f}))
(defn reg-event-fx
  ([id f] (el/reg {:id id :logic (fn [{:keys [event] :as ctx}] (f ctx event))}))
  ([id ins f] (el/reg {:id id :ins ins :logic (fn [{:keys [event] :as ctx}] (f ctx event))})))
(defn reg-fx [id f] (el/reg {:id id :effect f}))
(def dispatch el/dispatch)
(def dispatch-sync el/dispatch-sync)

(el/cfg :std-ins [[:db] [:fx] [:event]])
(el/reg {:id :db :input #(deref app-db) :transition #(reset! app-db %2)})
(el/reg {:id :fx :transition el/do-effects})
(el/reg {:id :event :input :event})
(el/reg {:id :dispatch :effect el/dispatch})
