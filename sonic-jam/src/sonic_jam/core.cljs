(ns sonic-jam.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [chan <! >!]]
            [chord.client :refer [ws-ch]]))

(enable-console-print!)

(defonce app-state (atom {:grids {}}))

(defn grids []
  (om/ref-cursor (:grids (om/root-cursor app-state))))

(defn cell-view [{:keys [cursor id]} _]
  (reify
    om/IRenderState
    (render-state [_ state]
      (dom/td #js {:onClick #(do
                               (om/update! cursor [(mod (inc (first cursor)) 2)])
                               (go (>! (:set-state-ch state) id)))
                   :style #js {:width "20px"
                               :fontSize "20px"}}
             (str " " (first cursor))))))

(declare grid-view)

(defn track-view [{:keys [cursor id]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:track-expanded true})
    om/IWillMount
    (will-mount [_]
      (when (= "grid" (get cursor "type"))
        (go (>! (om/get-state owner :get-state-ch) (get cursor "id")))))
    om/IRenderState
    (render-state [_ state]
      (if-not (:track-expanded state)
        (dom/tr nil (dom/td #js {:onClick #(om/set-state! owner :track-expanded true)
                                 :style #js {:color "#999"}} ">"))
        (dom/tr nil
                (dom/td #js {:onClick #(om/set-state! owner :track-expanded false)
                             :style #js {:color "#999"}} "<")
                (dom/td nil
                 (dom/table nil
                  (apply dom/tr nil
                         (let [cursors (map #(hash-map :cursor %1 :id %2)
                                            (get cursor "beats")
                                            (repeat id))]
                           (om/build-all cell-view cursors {:state state})))))
                (dom/td #js {:style #js {:color "#999" :paddingRight "10px"}} "{~}")
                (dom/td nil
                        (when (= "grid" (get cursor "type"))
                          (let [id (get cursor "id")
                                sub-state {:set-state-ch (:set-state-ch state)
                                           :get-state-ch (:get-state-ch state)}]
                            (om/build grid-view id {:state sub-state})))))))))

(declare track-builder)

(defn play-builder [{:keys [cursor id grid-state]} _]
  (reify
    om/IRender
    (render [_]
      (print "play-builder")
      (dom/p #js {:style #js {:color "#999"}}
             (dom/span #js {:onClick #(om/set-state! grid-state :builder-fn track-builder)} "{-")
             (dom/span nil " play options ")
             (dom/span nil "}")))))

(defn synth-builder [{:keys [cursor id grid-state]} _]
  (reify
    om/IRender
    (render [_]
      (print "synth-builder")
      (dom/p #js {:style #js {:color "#999"}}
             (dom/span #js {:onClick #(om/set-state! grid-state :builder-fn track-builder)} "{-")
             (dom/span nil " synth options ")
             (dom/span nil "}")))))

(defn sample-builder [{:keys [cursor id grid-state]} _]
  (reify
    om/IRender
    (render [_]
      (print "sample-builder")
      (dom/p #js {:style #js {:color "#999"}}
             (dom/span #js {:onClick #(om/set-state! grid-state :builder-fn track-builder)} "{-")
             (dom/select #js {:onChange #(print "bonk")}
                         (dom/option nil "0")
                         (dom/option nil "1"))
             (dom/span nil "}")))))

(defn type-builder [{:keys [cursor id grid-state]} _]
  (reify
    om/IRender
    (render [_]
      (print "type-builder")
      (let [merge-fn #(merge %3 {:builder-fn %1 :builder-type %2})
            set-type #(om/update-state! grid-state (partial merge-fn %1 %2))]
        (dom/p #js {:style #js {:color "#999"}}
               (dom/span #js {:onClick #(om/set-state! grid-state :builder-fn track-builder)} "{-")
               (dom/span #js {:onClick #(set-type play-builder "play")} " play ")
               (dom/span #js {:onClick #(set-type synth-builder "synth")} " synth ")
               (dom/span #js {:onClick #(set-type sample-builder "sample")} " sample ")
               (dom/span nil "}"))))))

(defn width-builder [{:keys [cursor id grid-state]} _]
  (reify
    om/IRender
    (render [_]
      (print "width-builder")
      (let [merge-fn #(merge %2 {:builder-fn type-builder :builder-width %1})
            set-width #(om/update-state! grid-state (partial merge-fn %))]
        (dom/p #js {:style #js {:color "#999"}}
               (dom/span #js {:onClick #(om/set-state! grid-state :builder-fn track-builder)} "{-")
               (dom/span #js {:onClick #(set-width 1)} " 1 ")
               (dom/span #js {:onClick #(set-width 2)} " 2 ")
               (dom/span #js {:onClick #(set-width 4)} " 4 ")
               (dom/span #js {:onClick #(set-width 8)} " 8 ")
               (dom/span #js {:onClick #(set-width 16)} " 16 ")
               (dom/span #js {:onClick #(set-width 32)} " 32 ")
               (dom/span #js {:onClick #(set-width 64)} " 64 ")
               (dom/span nil "}"))))))

(defn track-builder [{:keys [cursor id grid-state]} _]
  (reify
    om/IRender
    (render [_]
      (print "track-builder")
      (dom/p #js {:style #js {:color "#999"}}
             (dom/span #js {:onClick #(om/set-state! grid-state :builder-fn width-builder)} "{+}")))))

(defn grid-view [id owner]
  (reify
    om/IInitState
    (init-state [_]
      {:grid-expanded true ;; false
       :builder-fn track-builder
       :builder-state {}})
    om/IRenderState
    (render-state [_ state]
      (let [cursor (get (om/observe owner (grids)) id)]
        (dom/div #js {:style #js {:borderLeft "solid 3px #ccc"
                                  :paddingLeft "10px"}}
                 (if-not (:grid-expanded state)
                   (dom/div nil 
                            (dom/p #js {:onClick #(om/set-state! owner :grid-expanded true)
                                        :style #js {:color "#999"}}
                                   (str "[+] " id)))
                   (dom/div nil 
                            (dom/p #js {:onClick #(om/set-state! owner :grid-expanded false)
                                        :style #js {:color "#999"}}
                                   (str "[-] " id))
                            (apply dom/table nil
                                   (let [cursors (map #(hash-map :cursor %1 :id %2)
                                                      (get cursor "tracks")
                                                      (repeat id))]
                                     (om/build-all track-view cursors {:state state})))
                            (om/build (:builder-fn state) {:cursor cursor :id id :grid-state owner}))))))))

(defn app-view [cursor _]
  (reify
    om/IInitState
    (init-state [_]
      (let [set-state-ch (chan)
            get-state-ch (chan)]
        (go
          (let [{:keys [ws-channel error]} (<! (ws-ch "ws://127.0.0.1:4550/oscbridge"
                                                      {:format :json}))]
            (>! ws-channel {:Address "/get-state" :Params ["root"]})
            (>! ws-channel {:Address "/get-samples" :Params []})
            (go-loop []
              (let [ns (<! set-state-ch)
                    grid (get-in @cursor [:grids ns])
                    json (js/JSON.stringify (clj->js grid))
                    update #js {:Address "/set-state" :Params #js [ns json]}]
                (go (>! ws-channel update)))
              (recur))
            (go-loop []
              (let [ns (<! get-state-ch)
                    request #js {:Address "/get-state" :Params #js [ns]}]
                (go (>! ws-channel request)))
              (recur))
            (go-loop []
              (let [{:keys [message error] :as msg} (<! ws-channel)
                    params (get message "Params")]
                (cond 
                  (= "/state" (get message "Address"))
                  (let [grid (js->clj (js/JSON.parse (second params)))]
                    (om/update! cursor [:grids (first params)] grid))
                  (= "/samples" (get message "Address"))
                  (let [samples (js->clj (js/JSON.parse (first params)))]
                    (om/update! cursor :samples samples)))
                (when message
                  (recur))))))
        {:set-state-ch set-state-ch
         :get-state-ch get-state-ch}))
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:style #js {:fontFamily "monospace"}}
               (om/build grid-view "root" {:state state})))))
        
(om/root app-view app-state
  {:target (. js/document (getElementById "app"))})
