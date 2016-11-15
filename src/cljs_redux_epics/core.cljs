(ns cljs-redux-epics.core
  (:require [cljs.core.async :as async :refer [<! chan put!]]
            [jamesmacaulay.zelkova.signal :as z :refer [to-chan input]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(defn- epic-channel [dispatch! epic]
  (let [action-channel (chan)
        result-channel (-> (input {:type ::epic-init} ::epic action-channel)
                           (epic)
                           (to-chan))]
    (go-loop []
      (let [result-action (<! result-channel)]
        (dispatch! result-action)
        (recur)))
    action-channel))


(defn- combine-epics [dispatch! epics]
  (let [epic-channels (->> (conj epics identity)
                           (map (partial epic-channel dispatch!)))
        action-channel (chan)]
    (go-loop []
      (let [action (<! action-channel)]
        (doseq [epic-chan epic-channels]
          (put! epic-chan action))
        (recur)))
    action-channel))


(defn with-epics
  "Wraps a dispatcher adding zelkova signals to it.
   each epic will receive a 'actions' base signal that will change based on
   dispatched actions

   Example epic:
   (defn ping-pong [actions]
     (->> actions
       (z/keep-if #(= :ping (:type %)))
       (time/debounce 1000)
       (z/map (fn [] {:type :pong}))))

    the ping-pong epic will 'wait' for :ping actions, pause for 1 second then dispatch a :pong action.

    the reducer will first get a ping action then, after a second, a pong action"
  [dispatch! & epics]
  (let [action-channel (combine-epics dispatch! epics)]
    (fn [action]
      (put! action-channel action)
      action)))
