const getBooleanValue = (inputValue) => {
    var val = process.env[inputValue]
    if (typeof(val) === 'undefined' || val === null || val === '') {
        return false;
    }
    return /^true$/i.test(val);
}

export { getBooleanValue };