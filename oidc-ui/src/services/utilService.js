const getBooleanValue = (inputValue) => {
    if (typeof(inputValue) === 'undefined' || inputValue === null || inputValue === '') {
        return false;
    }
    return /^true$/i.test(inputValue);
}

export { getBooleanValue };