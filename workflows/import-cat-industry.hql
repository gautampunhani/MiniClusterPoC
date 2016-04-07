set mapred.job.name="import-cat-industry.hql";
set mapred.reduce.tasks=${noOfReducers};
set mapred.job.shuffle.input.buffer.percent=${shuffleInputBufferPercent};
set hive.mapred.supports.subdirectories=true;
set mapred.input.dir.recursive=true;
set hive.stats.autogather=false;
set mapreduce.map.memory.mb=5000;
set mapreduce.reduce.memory.mb=5000;
set mapreduce.map.java.opts=-Xmx5000m;
set mapreduce.reduce.java.opts=-Xmx5000m;

INSERT OVERWRITE TABLE ${dbName}.cat_industry PARTITION (dealercode='${dealercode}', currentMonth='${currentMonth}')
  SELECT *
  FROM ${olga_hadoop_db}.cat_industry
  WHERE dealer_code = '${dealercode}'