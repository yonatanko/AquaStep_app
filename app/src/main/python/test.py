import numpy as np

def main(x,y,z):
    np_array = np.array([x,y,z])
    return np.linalg.norm(np_array)

if __name__ == '__main__':
    main()