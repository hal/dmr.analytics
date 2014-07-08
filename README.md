# DMR Analytics

A bunch of utilities to analyze the WildFly management model.

Requires SBT to run and execute the core facilities and pandas/ipython to run the notebooks and analyze the model.

## Data export

The use spark to extract the data and do the munging. 
This step requires a running Wildfly instance to retrieve the model from. You can invoke data export through sbt:

`sbt run`

Then select the DMR related scripts. 

## Analytic tools
- http://pandas.pydata.org/
- http://ipython.org/

Once the python tools are installed you can launch the notebook server with 

`./notebook.sh`

## Examples

An example notebook can be found [here](http://nbviewer.ipython.org/github/hal/dmr.analytics/blob/master/notebooks/AttributeMetaData.ipynb)
