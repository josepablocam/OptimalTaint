library(shiny)
library(shinyAce)
source("analyze.R")
source("utilities.R")

shinyUI(pageWithSidebar(
  headerPanel('Instrumentation of Random Java Programs Implementing Core Syntax'),
  sidebarPanel(
    # measurement to plot (time/instruction count ratios)
    selectInput("measurement", label = h4("Measurement to Plot"), 
                choices = list("Execution time ratios" = 1, "Instruction count ratios" = 2)),
    # dynamic data options, defined in server.R
    uiOutput("data_choices"),
    downloadButton('downloadData', 'Download Data'),
    # load particular sample random program, defined in server.R
    uiOutput("code_choices"),
    # ACE editor for random code
    aceEditor("code", mode = 'java', value = get_random_program_text("P_0_none.java"))
  ),
  mainPanel(
    plotOutput('plot')
  )
))