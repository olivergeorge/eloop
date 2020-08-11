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
(defn reg-event-fx [id f] (el/reg {:kind :event :id id :f f}))
(defn reg-cofx [id f] (el/reg {:kind :input :id id :f f}))
(defn reg-fx [id f] (el/reg {:kind :action :id id :f f}))
(def dispatch el/dispatch)

(el/reg {:id :db :kind :fsm :state #(assoc % :db @app-db) :transition #(reset! app-db %)})
(el/reg {:id :fx :kind :fsm :state el/fx-state :transition el/fx-transition})
(el/reg {:id :event :kind :input :f (fn [s] (assoc s :event el/current-event))})
(el/reg {:id :args :kind :input :f (fn [s m] (update s :args merge m))})
(el/reg {:id :dispatch :kind :action :f dispatch})
