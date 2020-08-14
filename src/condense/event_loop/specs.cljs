(ns condense.event-loop.specs
  (:require [cljs.spec.alpha :as s]
            [condense.event-loop :refer [registry-ref cfg reg do-preloads do-event do-actions dispatch dispatch-sync]]))

(def registered-handler-id? #(get-in @registry-ref [:handlers %]))
(def registered-logic-id? #(get-in @registry-ref [:handlers % :logic]))
(def registered-action-id? #(get-in @registry-ref [:handlers % :action]))

(s/def ::id keyword?)
(s/def ::kind keyword?)
(s/def ::handlers (s/map-of :keyword map?))
(s/def ::event (s/cat :id registered-logic-id? :args (s/* any?)))
(s/def ::std-ins (s/coll-of (s/cat :id registered-handler-id? :args (s/* any?))))
(s/def ::preloads (s/map-of keyword? any?))
(s/def ::error fn?)

(s/fdef cfg :args (s/cat :k keyword? :v any?))
(s/fdef reg :args (s/cat :m (s/keys :req-un [::id])))
(s/fdef do-preloads :args (s/cat :ctx (s/keys :req-un [::handlers ::event] :opt-un [::std-ins])))
(s/fdef do-event :args (s/cat :ctx (s/keys :req-un [::handlers ::event] :opt-un [::std-ins ::preloads])))
(s/fdef do-actions :args (s/cat :cfg (s/keys :req-un [::error ::handlers]) :ms (s/coll-of (s/map-of registered-action-id? any?))))
(s/fdef dispatch :args (s/cat :v (s/spec ::event)))
(s/fdef dispatch-sync :args (s/cat :v (s/spec ::event)))
