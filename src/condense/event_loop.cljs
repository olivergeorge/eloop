(ns condense.event-loop
  (:require [cljs.core.async :refer [go chan <! >! close!]]))

(def log (partial js/console.log))
(def warn (partial js/console.warn))
(def error (partial js/console.error))

(def registry-ref (atom {}))
(def ^:dynamic current-event nil)

(defn find-in [m ks not-found]
  (or (get-in m ks)
      (do (warn ::find-in.not-found m ks) not-found)))

(defn reg
  [{:keys [kind id] :as m}]
  (swap! registry-ref assoc-in [kind id] m))

(defn do-event
  [{:keys [event fsm input]} [id & args :as v]]
  (letfn [(do-state [s {:keys [state]}]
            (state s))
          (do-input [s [id & args]]
            (apply (find-in input [id :f] identity) s args))
          (do-transition [[id & args]]
            (apply (find-in fsm [id :transition] identity) args))]
    (binding [current-event v]
      (let [{:keys [f ins]} (find-in event [id] {})
            s (reduce do-state {} (vals fsm))
            s (reduce do-input s ins)
            s' (apply (or f identity) s args)]
        (doseq [v s']
          (do-transition v))))))

(defn process-queue []
  (let [ch (chan 100)]
    (go (loop []
          (when-let [v (<! ch)]
            (try (do-event @registry-ref v)
                 (catch js/Error err (error ::process-queue-ch.err v err)))
            (recur))))
    ch))

(def queue-ch
  (do (some-> queue-ch close!)
      (process-queue)))

(defn dispatch [v]
  (go (>! queue-ch v)))

(defn dispatch-sync [v]
  (do-event @registry-ref v))

(defn fx-transition [ms]
  (doseq [m ms [id & args] m]
    (let [f (find-in @registry-ref [:action id :f] identity)]
      (apply f args))))

(comment
  (def app-db (r/atom {}))
  (reg {:id :db :kind :fsm :state #(assoc % :db @app-db) :transition #(reset! app-db %)})
  (reg {:id :fx :kind :fsm :state #(assoc % :fx []) :transition fx-transition})
  (reg {:id :event :kind :input :f (fn [s] (assoc s :event current-event))})
  (reg {:id :args :kind :input :f (fn [s m] (update s :args merge m))})
  (reg {:id :dispatch :kind :action :f dispatch}))