from fastapi import FastAPI
from typing import List
import random

app = FastAPI()

# XGBoostによる上昇確率の推論（モック）
@app.post("/predict/xgboost")
def predict_xgboost(features: List[float]):
    # TODO: 将来的に学習済みのXGBoostモデルを読み込んで推論する処理を実装
    # 現段階では0.0〜1.0のランダムな確率を返す
    
    # Javaから送られてきた特徴量（RSIやボラティリティ等）をログに表示
    print(f"【XGBoost】受信した特徴量データ: {features}")
    
    return random.uniform(0.0, 1.0)
    

# HMMによる市場レジーム判定（モック）
@app.post("/predict/hmm")
def predict_hmm(observations: List[float]):
    # TODO: 将来的にhmmlearn等を用いた判定ロジックを実装
    # 0:RANGE, 1:STRONG_TREND_UP, 2:STRONG_TREND_DOWN, 3:HIGH_VOLATILITY
    
    # Javaから送られてきた観測データ（収益率等）をログに表示
    print(f"【HMM】受信した観測データ: {observations}")
    
    return random.choice([0, 1, 2, 3])