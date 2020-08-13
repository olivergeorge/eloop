(ns condense.event-loop2
  "Experiment to try and facilitate async inputs & transitions.

   Tradeoffs
    +1 Facilitates working with async data sources.
    -1 Exceptions more obscure.
   "
  (:require [cljs.core.async :as async :refer [go chan <! >! close! put!]]
            [cljs.core.async.interop :refer-macros [<p!]]))

(def log (partial js/console.log))
(def warn (partial js/console.warn))
(def error (partial js/console.error))

(def registry-ref (atom {}))

(defn reg
  [{:keys [id] :as m}]
  (swap! registry-ref assoc-in [:handlers id] m))

(defn noop [x] (warn ::noop) x)
(defn v->chan [v] (doto (chan) (put! v)))
(defn p->chan [p] (go (try (<p! p) (catch js/Error err (ex-info "promise error" {} err)))))

(defn do-event
  "Gather inputs.  Run logic.  Process transitions."
  [{:keys [handlers event] :as ctx}]
  (letfn [(input-ch [[id & args]]
            (or (some-> (get-in handlers [id :input-ch]) (apply args))
                (some-> (get-in handlers [id :input-p]) (apply args) p->chan)
                (some-> (get-in handlers [id :input] noop) (apply args) v->chan)))
          (transition-ch [[id & args]]
            (or (some-> (get-in handlers [id :transition-ch]) (apply ctx args))
                (some-> (get-in handlers [id :transition-p]) (apply ctx args) p->chan)
                (some-> (get-in handlers [id :transition] noop) (apply ctx args) v->chan)))]
    (go (try (let [[id & args] event
                   {:keys [logic ins]} (get-in handlers [id] {})
                   input-chs (map input-ch ins)
                   input-vs (<! (async/map list input-chs))
                   s (zipmap (map first ins) input-vs)
                   s' (apply (or logic noop) s args)]
               (async/map identity (map transition-ch s')))
             (catch js/Error err (error ::do-event.err event err))))))

(defn process-queue []
  (let [event-ch (chan 100)]
    (go (loop []
          (when-let [event (<! event-ch)]
            (<! (do-event (assoc @registry-ref :event event)))
            (recur))))
    event-ch))

(def event-ch
  (do (some-> event-ch close!)
      (process-queue)))

(defn dispatch [event]
  (go (>! event-ch event)))

(defn do-actions
  [{:keys [handlers]} ms]
  (doseq [m ms [id & args :as v] m]
    (try (apply (get-in handlers [id :action] noop) args)
         (catch js/Error err (error ::do-actions.err v err)))))

(comment
  (do
    (js/console.clear)

    (def app-db (atom {}))
    (reg {:id :db :input #(deref app-db) :transition (fn [ctx db] (reset! app-db db))})
    (reg {:id :fx :input (constantly []) :transition (fn [ctx ms] (do-actions ctx ms))})
    (reg {:id :event :input :event})
    (reg {:id :args :input second})
    (reg {:id :dispatch :action dispatch})

    ; Input via API with callback
    (defn geolocation []
      (let [ch (chan)]
        (js/navigator.geolocation.getCurrentPosition
          #(put! ch (js->clj %))
          #(put! ch (ex-info "Failed to get current position" (js->clj %)))
          #js {:timeout 1000})
        ch))

    (reg {:id :geolocation :input-ch geolocation})

    ; Input via promise
    (defn execute-sql
      [sql values]
      (-> (sqlite/open-database)
          (.then (fn [db] (sqlite/execute-sql db sql values))
                 (fn [err] (ex-info "Query failed" {:sql sql :values values} err)))))

    (reg {:id :users :input-p #(go-execute-sql "SELECT * FROM USERS" [])})

    ; App logic
    (defn set-loading [s] (assoc-in s [:db :loading?] true))
    (defn set-users [s] (assoc-in s [:db :users] (get-in s [:users])))
    (defn clear-loading [s] (update s :db dissoc :loading?))
    (defn get-data [s] (update s :fx conj {:GET {:url "/endpoint/data" :cb #(dispatch [:get-resp %])}}))
    (defn get-resp [s] (assoc-in s [:db :data] (get-in s [:event 1])))
    (defn GET [{:keys [cb]}] (js/setTimeout #(cb {:results [1 2 3]}) 1000))

    ; Composing handlers
    (def ins [[:db] [:fx]])
    (reg {:id :bootstrap :ins [[:db] [:fx] [:users]] :logic (comp set-loading set-users get-data)})
    (reg {:id :get-resp :ins ins :logic (comp get-resp clear-loading)})
    (reg {:id :GET :action-ch GET})

    (dispatch [:bootstrap])))