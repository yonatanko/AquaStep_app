import numpy as np
import pandas as pd
from scipy import signal
import os
import re
from xgboost import XGBClassifier, XGBRegressor
import matplotlib.pyplot as plt
from sklearn.metrics import confusion_matrix, accuracy_score, precision_score, recall_score, f1_score
from sklearn.metrics import roc_auc_score, mean_absolute_error, mean_squared_error, r2_score
from scipy.stats import spearmanr, pearsonr
import seaborn as sns


def get_threshold(norms, activity):
    if activity == 1:
        return np.mean(norms) + 0.72 * np.std(norms)
    else:
        return np.mean(norms) + 0.69 * np.std(norms)


def get_distance(activity):
    if activity == 1:
        return 5
    else:
        return 5


def smooth_norm(norms, len_window=10):
    smooth_norm = [sum(norms[:len_window]) / len_window]
    for i in range(1, len(norms[1:]) - len_window + 1):
        window = norms[i:i + len_window - 1]
        smooth_norm.append(sum(window) / len_window)
    return smooth_norm


def count_steps_lower_bound(norms_activity, gap_perc=0.95, smooth=False):
    norms = [norm for act, norm in norms_activity]
    activity = [act for act, norm in norms_activity][0]
    if smooth:
        norms = smooth_norm(norms)

    threshold = get_threshold(norms, activity)
    steps = 0
    above_threshold = False
    for norm in norms:
        if norm > threshold and not above_threshold:
            above_threshold = True
            steps += 1
        elif norm <= gap_perc * threshold and above_threshold:
            above_threshold = False
    return steps


def manual_peak_detection(norms_activity, smooth=False):
    norms = [norm for act, norm in norms_activity]
    activity = [act for act, norm in norms_activity][0]
    if smooth:
        norms = smooth_norm(norms)

    threshold = get_threshold(norms, activity)
    steps = 0
    for i in range(1, len(norms) - 1):
        if norms[i] > norms[i - 1] and norms[i] > norms[i + 1]:
            if norms[i] > threshold:
                steps += 1
    return steps


def signal_find_peaks(norms_activity):
    norms = [norm for act, norm in norms_activity]
    activity = [act for act, norm in norms_activity][0]
    cut_off = get_threshold(norms, activity)
    return len(signal.find_peaks(norms, cut_off, distance=get_distance(activity))[0])


def first_peak(norms_activity):
    norms = [norm for act, norm in norms_activity]
    activity = [act for act, norm in norms_activity][0]
    cut_off = get_threshold(norms, activity)
    peaks_indices = signal.find_peaks(norms, cut_off, distance=get_distance(activity))[0]
    return peaks_indices[0]


def peaks_diff(norms_activity):
    norms = [norm for act, norm in norms_activity]
    activity = [act for act, norm in norms_activity][0]
    cut_off = get_threshold(norms, activity)
    norms_std = np.std(norms)
    diffs = []

    peaks_indices = signal.find_peaks(norms, cut_off, distance=get_distance(activity))[0]
    for i in range(len(peaks_indices) - 1):
        diffs.append((peaks_indices[i + 1] - peaks_indices[i]) / norms_std)
    return sum(diffs) / len(diffs)


# the pre process function you should use on different files
def prepare_df(files_path):
    files = os.listdir(files_path)
    i = 0
    index_to_identifier = {}
    all_df = pd.DataFrame({"time": [], "x": [], "y": [], "z": [], "activity": [], "num_of_steps": [], "identifier": [],
                           "user_activity_id": [], "num_of_records": []})

    for file in files:
        params = file.split('_')
        filepath = os.path.join('new_data_set', file)
        with open(filepath, 'r') as f:
            file_content = f.read().split("\n")
            group_num = params[0]
            activity = params[1]
            sample_index = params[2]
            user_sampled = params[3].split(".")[0]
            df = pd.read_csv("new_data_set/" + file, skiprows=5)

            time_col_name = [s for s in df.columns if "T" in s][0]
            x_col_name = [s for s in df.columns if "X" in s][0]
            y_col_name = [s for s in df.columns if "Y" in s][0]
            z_col_name = [s for s in df.columns if "Z" in s][0]

            steps_str = file_content[3].split(":")[1]
            num_of_steps = int(re.search(r'\d+', steps_str).group())

            index_to_identifier[i] = (group_num, activity, sample_index, user_sampled)
            df['activity'] = np.array([activity] * len(df[time_col_name]))
            df['num_of_steps'] = num_of_steps
            df['identifier'] = i
            df['user_activity_id'] = f"{group_num}_{activity}_{user_sampled}"
            df['num_of_records'] = len(df)

            min_time = df[time_col_name].min()
            df[time_col_name] = df[time_col_name] - min_time
            df = df.rename(columns={x_col_name: 'x', y_col_name: 'y', z_col_name: 'z', time_col_name: 'time'})
            all_df = pd.concat([all_df, df[
                ['time', 'x', 'y', 'z', 'activity', 'num_of_steps', 'identifier', 'user_activity_id',
                 "num_of_records"]]], ignore_index=True)
            i += 1

    all_df = all_df.drop(
        all_df[(all_df['time'] == 0) & (all_df['x'] == 0) & (all_df['y'] == 0) & (all_df['z'] == 0)].index)
    all_df = all_df.dropna(subset=['x', 'y', 'z'], axis=0)
    all_df["norm"] = np.linalg.norm(all_df[['x', 'y', 'z']], axis=1)
    all_df["std_norm"] = all_df.groupby('identifier')['norm'].transform('std')
    all_df["mean_norm"] = all_df.groupby('identifier')['norm'].transform('mean')

    class_df = all_df.drop_duplicates(subset=["identifier"])
    class_df = class_df[["mean_norm", "std_norm", 'activity', 'num_of_steps', 'identifier', 'user_activity_id', "num_of_records"]]

    reg_df = all_df.copy()
    reg_df.loc[all_df.activity == "walk", 'activity'] = 0
    reg_df.loc[all_df.activity == "run", 'activity'] = 1
    reg_df["activity"] = reg_df["activity"].astype("int")

    reg_df["norm_activity"] = list(zip(reg_df["activity"], reg_df["norm"]))
    reg_df["steps_with_lower_bound"] = reg_df.groupby('identifier')['norm_activity'].transform(
        lambda x: count_steps_lower_bound(x, gap_perc=0.85, smooth=False))
    reg_df["manual_peak_detection"] = reg_df.groupby('identifier')['norm_activity'].transform(
        lambda x: manual_peak_detection(list(x), smooth=False))
    reg_df["signal_find_peaks"] = reg_df.groupby('identifier')['norm_activity'].transform(
        lambda x: signal_find_peaks(list(x)))
    reg_df["above_mean"] = reg_df.groupby('identifier')['norm'].transform(lambda x: np.sum(x > x.mean()))
    reg_df["peak_diff"] = reg_df.groupby('identifier')['norm'].transform(lambda x: np.mean(np.abs(np.diff(x))))
    reg_df = reg_df.drop_duplicates(subset="identifier")

    return class_df, reg_df, index_to_identifier


# the pre process function i used for the given files
def my_files_prepare_df(files_path):
    files = os.listdir(files_path)
    i = 0
    index_to_identifier = {}
    all_df = pd.DataFrame({"time": [], "x": [], "y": [], "z": [], "activity": [], "num_of_steps": [], "identifier": [],
                           "user_activity_id": [], "num_of_records": []})

    unwanted_files = ["1_walk_4_1.csv", "22_run_4_1.csv", "20_walk_1_2.csv", "26_walk_5_1.csv"]
    unwanted_groups = ["28", "10"]
    illegal_float_format = ['16_run_3_1.csv',
                             '1_walk_4_1.csv',
                             '1_walk_4_1.csv',
                             '1_walk_4_1.csv',
                             '1_walk_4_1.csv',
                             '31_walk_2_1.csv',
                             '4_run_2_2.csv',
                             '4_walk_1_3.csv',
                             '4_walk_2_3.csv',
                             '4_walk_3_2.csv',
                             '4_walk_3_2.csv',
                             '5_run_3_1.csv',
                             '8_run_3_1.csv',
                             '8_walk_4_3.csv']
    unwanted_files.extend(illegal_float_format)

    for file in files:
        params = file.split('_')
        filepath = os.path.join('new_data_set', file)
        with open(filepath, 'r') as f:
            file_content = f.read().split("\n")
            group_num = params[0]
            activity = params[1]
            sample_index = params[2]
            user_sampled = params[3].split(".")[0]

            if file in unwanted_files or group_num in unwanted_groups:
                continue
            if file in ["6_run_3_1.csv", "6_run_4_1.csv", "6_walk_5_1.csv", "11_walk_1_1.csv", "11_walk_2_1.csv",
                        "11_walk_3_1.csv", "11_walk_5_1.csv"]:
                df = pd.read_csv("new_data_set/" + file, skiprows=6)
            else:
                df = pd.read_csv("new_data_set/" + file, skiprows=5)

            time_col_name = [s for s in df.columns if "T" in s][0]
            x_col_name = [s for s in df.columns if "X" in s][0]
            y_col_name = [s for s in df.columns if "Y" in s][0]
            z_col_name = [s for s in df.columns if "Z" in s][0]

            if "20" in file:
                steps_str = file_content[3].split(":")[2]
            elif "18" not in file and "29" not in file:
                steps_str = file_content[3].split(":")[1]
            else:
                steps_str = file_content[3].split(",")[1]
            num_of_steps = int(re.search(r'\d+', steps_str).group())

            if file == "9_walk_2_1.csv":
                df = df.drop(df.tail(2).index)

            if file == "4_walk_4_2.csv":
                df = df.fillna({x_col_name: -6.08})

            if file == "12_walk_5_1.csv":
                df[x_col_name] = df[x_col_name].replace(12594553.84, 8.08)
            if file == "7_run_1_1.csv":
                df[x_col_name] = df[x_col_name].replace(1255425.4, 5.02)
            if file == "7_walk_5_1.csv":
                df[x_col_name] = df[x_col_name].replace(555862.25, 0.98)
            if file == "4_walk_4_2.csv":
                df[x_col_name] = df[x_col_name].replace(65, -0.04)
            if file == "30_run_5_1.csv":
                df[y_col_name] = df[y_col_name].replace(4595570.55, -0.24)
            if file == "7_walk_3_1.csv":
                df[y_col_name] = df[y_col_name].replace(1194211.2, 0.04)

            index_to_identifier[i] = (group_num, activity, sample_index, user_sampled)
            df['activity'] = np.array([activity] * len(df[time_col_name]))
            df['num_of_steps'] = num_of_steps
            df['identifier'] = i
            df['user_activity_id'] = f"{group_num}_{activity}_{user_sampled}"
            df['num_of_records'] = len(df)

            min_time = df[time_col_name].min()
            df[time_col_name] = df[time_col_name] - min_time
            df = df.rename(columns={x_col_name: 'x', y_col_name: 'y', z_col_name: 'z', time_col_name: 'time'})
            all_df = pd.concat([all_df, df[
                ['time', 'x', 'y', 'z', 'activity', 'num_of_steps', 'identifier', 'user_activity_id',
                 "num_of_records"]]], ignore_index=True)
            i += 1

    all_df = all_df.drop(
        all_df[(all_df['time'] == 0) & (all_df['x'] == 0) & (all_df['y'] == 0) & (all_df['z'] == 0)].index)
    all_df = all_df.dropna(subset=['x', 'y', 'z'], axis=0)
    all_df["norm"] = np.linalg.norm(all_df[['x', 'y', 'z']], axis=1)
    all_df["std_norm"] = all_df.groupby('identifier')['norm'].transform('std')
    all_df["mean_norm"] = all_df.groupby('identifier')['norm'].transform('mean')

    class_df = all_df.drop_duplicates(subset=["identifier"])
    class_df = class_df[
        ["mean_norm", "std_norm", 'activity', 'num_of_steps', 'identifier', 'user_activity_id', "num_of_records"]]

    reg_df = all_df.copy()
    reg_df.loc[all_df.activity == "walk", 'activity'] = 0
    reg_df.loc[all_df.activity == "run", 'activity'] = 1
    reg_df["activity"] = reg_df["activity"].astype("int")

    reg_df["norm_activity"] = list(zip(reg_df["activity"], reg_df["norm"]))
    reg_df["steps_with_lower_bound"] = reg_df.groupby('identifier')['norm_activity'].transform(
        lambda x: count_steps_lower_bound(x, gap_perc=0.85, smooth=False))
    reg_df["manual_peak_detection"] = reg_df.groupby('identifier')['norm_activity'].transform(
        lambda x: manual_peak_detection(list(x), smooth=False))
    reg_df["signal_find_peaks"] = reg_df.groupby('identifier')['norm_activity'].transform(
        lambda x: signal_find_peaks(list(x)))
    reg_df["above_mean"] = reg_df.groupby('identifier')['norm'].transform(lambda x: np.sum(x > x.mean()))
    reg_df["peak_diff"] = reg_df.groupby('identifier')['norm'].transform(lambda x: np.mean(np.abs(np.diff(x))))
    reg_df = reg_df.drop_duplicates(subset="identifier")

    return class_df, reg_df, index_to_identifier

def split_train_test_classification(df):
    all_users = df["user_activity_id"].nunique()
    train_users = df["user_activity_id"].sample(n=int(0.9 * (all_users)), random_state=10)
    train_df = df[df["user_activity_id"].isin(train_users)]
    test_df = df[~df["user_activity_id"].isin(train_users)]
    X_train, y_train = train_df[["mean_norm", "std_norm", "identifier"]], train_df["activity"]
    X_test, y_test = test_df[["mean_norm", "std_norm", "identifier"]], test_df["activity"]
    return X_train, y_train, X_test, y_test


def split_train_test_regression(df):
    all_users = df["user_activity_id"].nunique()
    train_users = reg_df["user_activity_id"].sample(n=int(0.9 * (all_users)), random_state=100)
    train_df = reg_df[reg_df["user_activity_id"].isin(train_users)]
    test_df = reg_df[~reg_df["user_activity_id"].isin(train_users)]
    X_train, y_train = train_df[["peak_diff", "activity", "above_mean", "steps_with_lower_bound", "manual_peak_detection",
                                "signal_find_peaks"]], train_df["num_of_steps"]
    X_test, y_test = test_df[["peak_diff", "activity", "above_mean", "steps_with_lower_bound", "manual_peak_detection",
                              "signal_find_peaks"]], test_df["num_of_steps"]
    return X_train, y_train, X_test, y_test


def evaluate_classification_model(y_true, y_pred):
    accuracy = accuracy_score(y_true, y_pred)
    print("Accuracy:", accuracy)
    precision = precision_score(y_true, y_pred)
    print("Precision:", precision)
    recall = recall_score(y_true, y_pred)
    print("Recall:", recall)
    f1 = f1_score(y_true, y_pred)
    print("F1 Score:", f1)
    auc = roc_auc_score(y_true, y_pred)
    print("AUC-ROC:", auc)
    mae = mean_absolute_error(y_true, y_pred)
    print("Mean Absolute Error:", mae)
    rmse = np.sqrt(mean_squared_error(y_true, y_pred))
    print("Root Mean Squared Error:", rmse)
    cm = confusion_matrix(y_true, y_pred)
    sns.heatmap(cm, annot=True, fmt='d')
    plt.show()


def evaluate_regression_model(y_true, y_pred):
    series_y_true = pd.Series(y_true).reset_index(drop=True)
    series_y_pred = pd.Series(y_pred).reset_index(drop=True)
    y_true_sorted = series_y_true.sort_values()
    series_y_pred = series_y_pred[y_true_sorted.index]
    rmse = np.sqrt(mean_squared_error(y_true, y_pred))
    print("Root Mean Squared Error:", rmse)
    r2 = r2_score(y_true, y_pred)
    print("R^2 Score:", r2)
    pearson_corr, _ = pearsonr(y_true, y_pred)
    print("Pearson Correlation:", pearson_corr)
    spearman_corr, _ = spearmanr(y_true, y_pred)
    print("Spearman Correlation:", spearman_corr)
    plt.scatter(range(len(y_true)), y_true_sorted, label="y_true")
    plt.scatter(range(len(y_true)), series_y_pred, label="y_pred")
    plt.legend()
    plt.title("Prediction errors")
    plt.show()


def run_and_eval_class(model, X_train, y_train, X_test, y_test):
    y_train = y_train.replace({"run": 1, "walk": 0})
    y_test = y_test.replace({"run": 1, "walk": 0})
    model.fit(X_train.drop("identifier", axis=1), y_train)
    y_pred = model.predict(X_test.drop("identifier", axis=1))
    evaluate_classification_model(y_true=y_test, y_pred=y_pred)
    return y_pred, model


def run_and_eval_reg(model, X_train, y_train, X_test, y_test):
    model.fit(X_train, y_train)
    y_pred = model.predict(X_test)
    y_pred = y_pred.astype(int)
    evaluate_regression_model(y_test, y_pred)


if __name__ == '__main__':
    """change the first line with your path to the files folder"""
    """run on our files"""
    class_df, reg_df, index_to_identifier = my_files_prepare_df("new_data_set")

    """run on different files"""
    # class_df, reg_df, index_to_identifier = prepare_df("new_data_set")

    # leave unchanged
    X_train_class, y_train_class, X_test_class, y_test_class = split_train_test_classification(class_df)
    X_train_reg, y_train_reg, X_test_reg, y_test_reg = split_train_test_regression(reg_df)
    class_model = XGBClassifier(n_estimators=100, max_depth=10, eta=0.05, gamma=0.1, reg_lambda=0.8, min_child_weight=2)
    reg_model = XGBRegressor(n_estimators=100, max_depth=10, eta=0.05, gamma=0.1, reg_lambda=0.8, min_child_weight=2)
    print("---------------------------------------- ACTIVITY CLASSIFICATION EVALUATION ----------------------------------------")
    run_and_eval_class(class_model, X_train_class, y_train_class, X_test_class, y_test_class)
    print()
    print("-------------------------------------------- STEPS REGRESSION EVALUATION ------------------------------------------")
    run_and_eval_reg(reg_model, X_train_reg, y_train_reg, X_test_reg, y_test_reg)

