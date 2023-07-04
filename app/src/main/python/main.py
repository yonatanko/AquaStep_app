import numpy as np
import pandas as pd
from scipy import signal
import pickle


def get_threshold(norms, activity):
    if activity == 1:
        return np.mean(norms) + 0.72 *np.std(norms)
    else:
        return np.mean(norms) + 0.69 *np.std(norms)


def get_distance(activity):
    return 5


def smooth_norm(norms, len_window=10):
    smooth_norm = [sum(norms[:len_window]) / len_window]
    for i in range(1, len(norms[1:]) - len_window + 1):
        window = norms[i: i +len_window - 1]
        smooth_norm.append(sum(window) / len_window)
    return smooth_norm


def count_steps_lower_bound(norms, activity, gap_perc=0.95, smooth=False):
    if smooth:
        norms = smooth_norm(norms)

    threshold = get_threshold(norms, activity)
    steps = 0
    above_threshold = False
    for norm in norms:
        if norm > threshold and not above_threshold:
            above_threshold = True
            steps += 1
        elif norm <= gap_perc *threshold and above_threshold:
            above_threshold = False
    return steps


def manual_peak_detection(norms, activity, smooth=False):
    if smooth:
        norms = smooth_norm(norms)

    threshold = get_threshold(norms, activity)
    steps = 0
    for i in range(1, len(norms) - 1):
        if norms[i] > norms[ i -1] and norms[i] > norms[ i +1]:
            if norms[i] > threshold:
                steps += 1
    return steps


def signal_find_peaks(norms, activity):
    cut_off = get_threshold(norms, activity)
    return len(signal.find_peaks(norms, cut_off, distance=get_distance(activity))[0])


def first_peak(norms, activity):
    cut_off = get_threshold(norms, activity)
    peaks_indices = signal.find_peaks(norms, cut_off, distance=get_distance(activity))[0]
    return peaks_indices[0]


def peaks_diff(norms, activity):
    cut_off = get_threshold(norms, activity)
    diffs = []

    peaks_indices = signal.find_peaks(norms, cut_off, distance=get_distance(activity))[0]
    for i in range(len(peaks_indices) - 1):
        diffs.append((peaks_indices[ i +1] - peaks_indices[i]))
    return sum(diffs) / len(diffs)


def get_preds(file_path):
    reg_model = pickle.load(open("/sdcard/reg_xgboost_model.pickle", 'rb'))
    class_model = pickle.load(open("/sdcard/all_activties_class_xgboost_model.pickle", 'rb'))
    df = pd.read_csv(file_path)
    df = df.dropna()

    df["norm"] = np.linalg.norm(df, axis=1)
    df["mean_norm"] = df["norm"].mean()
    df["std_norm"] = df["norm"].std()
    class_df = df[["mean_norm", "std_norm"]].drop_duplicates()
    pred_activity = class_model.predict(class_df)[0]

    # rest
    if pred_activity == 2:
        steps_num = 0

    else:
        # 0 walk, 1 run
        norms = np.array(df["norm"])
        reg_df = df.copy()
        reg_df["steps_with_lower_bound"] = count_steps_lower_bound(norms, pred_activity, gap_perc=0.85, smooth=False)
        reg_df["manual_peak_detection"] = manual_peak_detection(norms, pred_activity, smooth=False)
        reg_df["signal_find_peaks"] = signal_find_peaks(norms, pred_activity)
        reg_df["above_mean"] = np.sum(norms > norms.mean())
        reg_df["peak_diff"] = np.mean(np.abs(np.diff(norms)))
        reg_df["activity"] = pred_activity
        reg_df = reg_df[["peak_diff", "activity", "above_mean", "steps_with_lower_bound", "manual_peak_detection", "signal_find_peaks"]].drop_duplicates()
        steps_num = int(round(reg_model.predict(reg_df)[0], 1))

    return pred_activity, steps_num

if __name__ == '__main__':
    get_preds()
