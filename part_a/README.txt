Running part A instructions: 

In order to run part A's models, you need to run our "run_part_a.py" file.
The only change required is to modify the first line of the main function:

- if you want to run the code on our original files (Our files are attached in the submission folder) you need to run: 
class_df, reg_df, index_to_identifier = my_files_prepare_df("new_data_set")

This function cleans the files (based on the errors we found. More details on these errors in the "project_part_a.ipynb" notebook)
and returns the model's dataframes.

- if you want to run the code on different files (assuming these files do not contain errors nor corrupted), you need to run:
class_df, reg_df, index_to_identifier = prepare_df("new_data_set")

This function makes the pre-process without deleting or correcting the files and returns the model's dataframes. 

In both cases, replace the "new_data_set" argument with the correct path to the files' directory. 
Do not change any additional line in the main section.
