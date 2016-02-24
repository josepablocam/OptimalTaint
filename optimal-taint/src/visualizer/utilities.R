# Utility functions

############## Data Manipulation ############## 

except <- function(l, x) {
  l[!l %in% x]
}

############## Shiny App ############## 
# for dynamic menus
vals_to_options <- function(x) {
  ops <- as.list(x)
  names(ops) <- x
  ops
}

# read java files for ace editor
get_program_names <- function() {
  list.files("./data/sample_java/", pattern= "*.java")
}

# get body of file for ace editor
get_random_program_text <- function(file_name) {
  path <- paste(c("./data/sample_java", file_name), collapse = "/")
  print(path)
  paste(readLines(path), collapse = "\n")
}