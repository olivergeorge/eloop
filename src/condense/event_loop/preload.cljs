(ns condense.event-loop.preload
  (:require [cljs.spec.alpha :as s]
            [cljs.spec.test.alpha :as stest]
            [condense.event-loop.specs]))

(stest/instrument)
(s/check-asserts true)