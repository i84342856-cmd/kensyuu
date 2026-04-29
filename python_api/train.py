import pandas as pd
import numpy as np
import xgboost as xgb
from hmmlearn import hmm
import joblib
from sklearn.preprocessing import StandardScaler
import os

csv_file = 'training_data.csv'
if not os.path.exists(csv_file): exit()

df = pd.read_csv(csv_file)
print("🧠 マルチタイムフレームAIの学習を開始します...")

# 時間足ごとのIDリスト
timeframe_ids = df['TimeframeID'].unique()

for tf_id in timeframe_ids:
    print(f"⚙️ 時間足ID [{tf_id}] の専用AIを構築中...")
    
    # その時間足のデータだけを抽出
    df_tf = df[df['TimeframeID'] == tf_id]
    X = df_tf[['RSI', 'StdDevPct', 'MADev', 'F4', 'F5', 'F6', 'F7', 'VolRatio', 'TimeframeID']]
    y = df_tf['Target']

    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)

    # XGBoost
    clf = xgb.XGBClassifier(n_estimators=200, max_depth=4, learning_rate=0.05, random_state=42)
    clf.fit(X_scaled, y)

    # HMM
    hmm_model = hmm.GaussianHMM(n_components=4, covariance_type="diag", n_iter=100, random_state=42)
    hmm_model.fit(X_scaled)

    # 時間足ごとの名前で保存 (例: xgboost_model_60.joblib)
    joblib.dump(clf, f'xgboost_model_{int(tf_id)}.joblib')
    joblib.dump(hmm_model, f'hmm_model_{int(tf_id)}.joblib')
    joblib.dump(scaler, f'scaler_{int(tf_id)}.joblib')

print("🎉 全時間足のAI脳みそ（.joblib群）が生成されました！")