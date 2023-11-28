const getBooleanValue = (inputValue) => {
    var val = process.env[inputValue]
    console.log(inputValue, val)
    console.log(typeof val)
    if (typeof(val) === 'undefined' || val === null || val === '') {
        console.log("no input provided", inputValue)
        return false;
    }
    console.log("valid input provided", inputValue)
    return /^true$/i.test(val);
}

export { getBooleanValue };