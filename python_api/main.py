from fastapi import FastAPI, HTTPException
from typing import List
import joblib
import numpy as np
import os

app = FastAPI()

# --- 起動時にすべての脳みそをロードして辞書(dict)に保管 ---
models = {'xgb': {}, 'hmm': {}, 'scaler': {}}
# 💡【修正】10080(W1) を追加
supported_tfs = [5, 15, 30, 60, 240, 1440, 10080]

for tf in supported_tfs:
    try:
        models['xgb'][tf] = joblib.load(f"xgboost_model_{tf}.joblib")
        models['hmm'][tf] = joblib.load(f"hmm_model_{tf}.joblib")
        models['scaler'][tf] = joblib.load(f"scaler_{tf}.joblib")
        print(f"✅ 時間足 {tf} のモデル読み込み成功")
    except Exception as e:
        pass # ファイルがない場合はスキップ

@app.post("/predict/xgboost")
def predict_xgboost(features: List[float]):
    tf_id = int(features[8]) 
    
    if tf_id not in models['xgb'] or models['xgb'][tf_id] is None:
        return 0.5 
    
    try:
        clf = models['xgb'][tf_id]
        scaler = models['scaler'][tf_id]
        X = np.array(features).reshape(1, -1)
        X_scaled = scaler.transform(X)
        return float(clf.predict_proba(X_scaled)[0][1])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/predict/hmm")
def predict_hmm(observations: List[float]):
    tf_id = int(observations[8])
    if tf_id not in models['hmm'] or models['hmm'][tf_id] is None: 
        return 0
    
    try:
        hmm_model = models['hmm'][tf_id]
        scaler = models['scaler'][tf_id]
        
        # 💡【修正】HMMもXGBoostと同じようにスケーリング処理を通す
        X = np.array(observations).reshape(1, -1)
        X_scaled = scaler.transform(X)
        
        return int(hmm_model.predict(X_scaled)[0])
    except Exception as e: 
        print(f"HMM Error: {e}")
        return 0