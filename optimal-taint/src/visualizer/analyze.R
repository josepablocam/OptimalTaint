# Set of functions to analyze/plot results of experiments
library(plyr)
library(reshape2)
library(ggplot2)
source("utilities.R")

############## Data Manipulation ############## 
# filter out "bad" observations (caliper runs multiple measurements to warm up vm etc)
filter_results <- function(dat) {
  TAKE_N <- 5
  # take last 5  observations
  # TODO: this should be done empirically (once execution time stablizes)
  ddply(dat, .(name, declaration_count, generated_command_min_count), function(x) tail(x, TAKE_N))
}

# add some useful columns (duplicates information, but easier for downstream)
extend_results <- function(dat) {
  dat$instrumentation_type <- ifelse(grepl("none", dat$name), "None", "Naive")
  dat$simple_name <- sapply(as.character(dat$nam), function (x) paste0(strsplit(x, "_")[[1]][-3], collapse= "_"))
  dat
}

# Create average and standard deviations for ratios of instrumented to none
multiples_summary <- function(dat) {
  keys <- c("simple_name", "instrumentation_type", "declaration_count", "generated_command_min_count")
  vals <- c("execution_time", "instruction_count")
  cols <- c(keys, vals)
  flat_dat <- melt(dat[, cols], keys)
  flat_dat <- ddply(flat_dat, c(keys, "variable"), transform, id = seq_along(value))
  pivot_dat <- dcast(flat_dat, simple_name + declaration_count + generated_command_min_count + variable + id ~ instrumentation_type, sum,  value.var = "value")
  pivot_dat$naive_to_none <- pivot_dat$Naive / pivot_dat$None
  multiples <- ddply(pivot_dat, c(except(keys, "instrumentation_type"), "variable"), summarize, 
                     mean_naive_to_none = mean(naive_to_none),
                     sd_naive_to_none = sd(naive_to_none)
  )
 multiples
}

############## Data Plots ############## 
# Generic plotting of ratios summary
multiples_graph <- function(dat, variable_name) {
  dat <- subset(dat, variable == variable_name)
  dat$declaration_count <- paste(dat$declaration_count, "vars")
  dat$generated_command_min_count <- paste(dat$generated_command_min_count, "min commands in code")
  ggplot(dat, aes(x = simple_name, y = mean_naive_to_none)) +
    geom_bar(stat = "identity", aes(fill = "Average")) +
    geom_errorbar(aes(
      ymin = mean_naive_to_none - sd_naive_to_none, 
      ymax = mean_naive_to_none + sd_naive_to_none,
      color = "Avg +/- sd")) +
    facet_grid(declaration_count ~ generated_command_min_count) +
    labs(
      x = "Program Name", 
      y = "(Naive Instr. Exec. Time) / (No Instr. Exec. Time)",
      fill = "", 
      color = "") +
    scale_fill_manual(values = c("dodgerblue2"))
}

# plot average ratios of execution time
exec_time_graph <- function(dat) {
  multiples_graph(dat, "execution_time")
}

# plot average ratios of bytecode instructions
instr_ct_graph <- function(dat) {
  multiples_graph(dat, "instruction_count") + labs(y = "(Naive Instr. Instruction Ct) / (No Instr. Instruction Ct)")
}

