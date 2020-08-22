(ns condense.event-loop)

(def registry-ref
  (atom {:std-ins     {}
         :dispatch-fn first
         :error       (partial js/console.error)}))

(defn cfg [k v] (swap! registry-ref assoc k v))
(defn reg [{:keys [id] :as m}] (swap! registry-ref assoc-in [:handlers id] m))

(defn do-error
  [{:keys [error] :as ctx} msg data err]
  (when error
    (error (ex-info msg (with-meta data ctx) err))))

(defn do-log
  [{:keys [handlers log] :as ctx} k & args]
  (when-let [log (get-in handlers [k :log] log)]
    (apply log ctx k args)))

(defn do-preload
  [{:keys [handlers dispatch-fn] :as ctx} [k data]]
  (let [id (dispatch-fn [k data])]
    (when-let [f (get-in handlers [id :preload])]
      (do-log ctx ::do-preload data)
      [k (f ctx data)])))

(defn do-preloads
  [{:keys [std-ins handlers event dispatch-fn] :as ctx}]
  (do-log ctx ::do-preloads)
  (let [id (dispatch-fn event)
        ins (merge std-ins (get-in handlers [id :ins]))
        kps (keep (partial do-preload ctx) ins)]
    (-> (js/Promise.all (map second kps))
        (.then (fn [vs] (assoc ctx :preloads (zipmap (map first kps) vs)))))))

(defn do-input
  [{:keys [handlers dispatch-fn] :as ctx} [k data]]
  (do-log ctx ::do-input [k data])
  (let [id (dispatch-fn data)]
    (when-let [f (get-in handlers [id :input])]
      [k (f ctx data)])))

(defn do-transition
  [{:keys [handlers] :as ctx} [id data]]
  (when-let [f (get-in handlers [id :transition])]
    (do-log ctx ::do-transition data)
    (f ctx data)))

(defn do-event
  [{:keys [std-ins handlers event preloads dispatch-fn] :as ctx}]
  (do-log ctx ::do-event)
  (let [id (dispatch-fn event)
        logic (get-in handlers [id :logic] identity)
        ins (merge std-ins (get-in handlers [id :ins]))
        s (into preloads (keep (partial do-input ctx) ins))
        s' (logic s event)]
    (doall (map (partial do-transition ctx) s'))))

(defn do-effect
  [{:keys [handlers dispatch-fn] :as ctx} data]
  (do-log ctx ::do-effect data)
  (let [id (dispatch-fn data)]
    (when-let [f (get-in handlers [id :effect])]
      (f data))))

(defn do-effects
  [ctx ms]
  (do-log ctx ::do-effects ms)
  (doseq [m ms v m :when v]
    (try (do-effect ctx v)
         (catch js/Error err (do-error ctx ::do-effects.err {:v v} err)))))

(defn init-ctx [event]
  (assoc @registry-ref :event event))

(defn do-dispatch
  [ctx]
  (do-log ctx ::do-dispatch)
  (-> (do-preloads ctx)
      (.then do-event)
      (.catch (fn [err] (do-error ctx ::do-dispatch.err {} err)))))

(defn dispatch [event]
  (do-dispatch (init-ctx event)))

(defn dispatch-sync [event]
  (do-event (init-ctx event)))

(do
  (def app-db (atom {}))
  (cfg :std-ins {:db [:db] :event [:event]})
  (defn form-logger [ctx k & args]
    (when (#{::do-event ::do-transition} k)
      (js/console.log (:event ctx) (with-meta (apply list (symbol k) 'ctx args) ctx))))
  (cfg :log form-logger)
  (reg {:id :db :input #(deref app-db) :transition #(reset! app-db %2)})
  (reg {:id :fx :transition do-effects})
  (reg {:id :event :input :event})
  (reg {:id :dispatch :effect dispatch})
  (defn set-loading [s] (assoc-in s [:db :loading?] true))
  (defn fetch-data [s] (-> s (update :fx conj {:GET "/some/url"})))
  (reg {:id :bootstrap :logic (comp set-loading fetch-data)})
  (dispatch [:bootstrap]))