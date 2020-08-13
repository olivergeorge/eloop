(ns condense.event-loop
  (:require [cljs.core.async :refer [go chan <! >! close!]]))

(def log (partial js/console.log))
(def warn (partial js/console.warn))
(def error (partial js/console.error))

(def registry-ref (atom {}))

(defn reg
  [{:keys [id] :as m}]
  (swap! registry-ref assoc-in [:handlers id] m))

(defn noop [x] (warn ::noop) x)

(defn do-event
  [{:keys [handlers event] :as ctx}]
  (letfn [(do-input [[id & args]]
            (apply (get-in handlers [id :input] noop) ctx args))
          (do-transition [[id & args]]
            (apply (get-in handlers [id :transition] noop) ctx args))]
    (let [[id & args] event
          {:keys [logic ins]} (get handlers id)
          s (zipmap (map first ins) (map do-input ins))
          s' (apply (or logic noop) s args)]
      (doseq [v s'] (do-transition v)))))

(defn process-queue []
  (let [ch (chan 100)]
    (go (loop []
          (when-let [event (<! ch)]
            (try (do-event (assoc @registry-ref :event event))
                 (catch js/Error err (error ::process-queue-ch.err event err)))
            (recur))))
    ch))

(def queue-ch
  (do (some-> queue-ch close!)
      (process-queue)))

(defn dispatch [event]
  (go (>! queue-ch event)))

(defn dispatch-sync [event]
  (do-event (assoc @registry-ref :event event)))

(defn do-actions
  [{:keys [handlers]} ms]
  (doseq [m ms [id & args :as v] m]
    (try (apply (get-in handlers [id :action] noop) args)
         (catch js/Error err (error ::fx-transition.err v err)))))

(comment
  (do (def app-db (atom {}))
      (reg {:id :db :input #(deref app-db) :transition #(reset! app-db %2)})
      (reg {:id :fx :input (constantly []) :transition do-actions})
      (reg {:id :event :input :event})
      (reg {:id :args :input second})
      (reg {:id :dispatch :action dispatch})
      (dispatch [:bootstrap])))