(ns condense.event-loop)

(def registry-ref
  (atom {:std-ins []
         :log     (partial js/console.log)
         :error   (partial js/console.error)}))

(defn cfg [k v] (swap! registry-ref assoc k v))
(defn reg [{:keys [id] :as m}] (swap! registry-ref assoc-in [:handlers id] m))

(defn do-error
  [{:keys [error] :as ctx} msg data err]
  (when error
    (error (ex-info msg (with-meta data {:ctx ctx}) err))))

(defn do-log
  [{:keys [event log] :as ctx} k & args]
  (when log
    (log event (with-meta (apply list (symbol k) 'ctx args) {:ctx ctx}))))

(defn do-preload
  [{:keys [handlers] :as ctx} [id & args :as v]]
  (do-log ctx ::do-preload v)
  (let [f (get-in handlers [id :preload] #())]
    [id (apply f args)]))

(defn do-preloads
  [{:keys [std-ins handlers event] :as ctx}]
  (do-log ctx ::do-preloads)
  (let [eid (first event)
        ins (into std-ins (get-in handlers [eid :ins]))
        kps (map (partial do-preload ctx) ins)]
    (-> (js/Promise.all (map second kps))
        (.then (fn [vs] (assoc ctx :preloads (zipmap (map first kps) vs)))))))

(defn do-input
  [{:keys [handlers preloads] :as ctx} [id & args :as v]]
  (do-log ctx ::do-input v)
  (or (some-> (get-in handlers [id :input]) (apply ctx args))
      (get preloads id)))

(defn do-transition
  [{:keys [handlers] :as ctx} [id & args :as v]]
  (do-log ctx ::do-transition v)
  (some-> (get-in handlers [id :transition]) (apply ctx args)))

(defn do-event
  [{:keys [std-ins handlers event] :as ctx}]
  (do-log ctx ::do-event)
  (let [[eid & args] event
        logic (get-in handlers [eid :logic] identity)
        ins (into std-ins (get-in handlers [eid :ins]))
        s (zipmap (map first ins) (map (partial do-input ctx) ins))
        s' (apply logic s args)]
    (doall (map (partial do-transition ctx) s'))))

(defn do-effect
  [{:keys [handlers] :as ctx} [id & args :as v]]
  (do-log ctx ::do-effect v)
  (apply (get-in handlers [id :effect] #()) args))

(defn do-effects
  [ctx ms]
  (do-log ctx ::do-effects ms)
  (doseq [m ms v m :when v]
    (try (do-effect ctx v)
         (catch js/Error err (do-error ctx ::do-effects.err {:v v} err)))))

(defn init-ctx [event]
  (assoc @registry-ref :event event))

(defn dispatch [event]
  (let [ctx (init-ctx event)]
    (-> (do-preloads ctx)
        (.then do-event)
        (.catch (fn [err] (do-error ctx ::do-dispatch.err {} err))))))

(defn dispatch-sync [event]
  (do-event (assoc @registry-ref :event event)))

(comment
  (def app-db (r/atom {}))
  (cfg :std-ins [[:db] [:event]])
  (reg {:id :db :input #(deref app-db) :transition #(reset! app-db %2)})
  (reg {:id :fx :transition do-effects})
  (reg {:id :event :input :event})
  (reg {:id :dispatch :effect dispatch})
  (dispatch [:bootstrap]))