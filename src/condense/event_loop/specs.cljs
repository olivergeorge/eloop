(ns condense.event-loop.specs
  (:require [cljs.spec.alpha :as s]
            [condense.event-loop :as el]))

(def registered-event-id? #(get-in @el/registry-ref [:event %]))
(def registered-fsm-id? #(get-in @el/registry-ref [:fsm %]))
(def registered-input-id? #(get-in @el/registry-ref [:input %]))
(def registered-action-id? #(get-in @el/registry-ref [:action %]))

(s/def ::id keyword?)
(s/def ::kind keyword?)
(s/def ::event-v (s/cat :id registered-event-id? :args (s/* any?)))
(s/def ::input-v (s/cat :id registered-input-id? :args (s/* any?)))
(s/def ::fsm-v (s/cat :id registered-fsm-id? :args (s/* any?)))

(s/fdef el/reg :args (s/cat :m (s/keys :req-un [::id ::kind])))
(s/fdef el/do-event :args (s/cat :cfg (s/keys :req-un [::event ::fsm ::input]) :v (s/spec ::event-v)))
(s/fdef el/dispatch :args (s/cat :v (s/spec ::event-v)))
(s/fdef el/dispatch-sync :args (s/cat :v (s/spec ::event-v)))
(s/fdef el/db-state :args (s/cat :s map?))
(s/fdef el/db-transition :args (s/cat :db map?))
(s/fdef el/fx-state :args (s/cat :s map?))
(s/fdef el/fx-transition :args (s/cat :fx (s/coll-of (s/map-of registered-action-id? any?))))
