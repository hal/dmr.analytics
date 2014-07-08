# DMR Analytics

A bunch of utilities to analyze the WildFly management model.

Requires SBT to run and execute the core facilities and pandas/ipython the run the notebooks used to analyze the model.

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

