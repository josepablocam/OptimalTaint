library(shiny)
library(shinyAce)
library(ggplot2)
source("analyze.R")
source("utilities.R")

shinyServer(function(input, output, session) {

  # Read in data and modify initially, avoid having to do it
  # on every call of plots etc
  raw_data <- read.csv("./data/results.csv")
  raw_data <- filter_results(raw_data)
  raw_data <- extend_results(raw_data)
  data <- multiples_summary(raw_data)
  
  # Download underlying raw data
  output$downloadData <- downloadHandler(
    filename = function() { "data.csv" },
    content = function(file) {
      write.csv(raw_data, file, row.names = F)
    }
  )
  
  # dynamic menu options (based on data set)
  declaration_counts_options <- vals_to_options(unique(data$declaration_count))
  min_command_counts_options <- vals_to_options(unique(data$generated_command_min_count))
  
  # Render these dynamic menu options
  output$data_choices <- renderUI({
    list(checkboxGroupInput("variable_cts", label = h5("Variable Declaration Count"), 
                       choices = declaration_counts_options,
                       #selected = declaration_counts_options
                       selected = c(100)
                       ),
         
    checkboxGroupInput("command_cts", label = h5("Minimum Command Count"), 
                       choices = min_command_counts_options,
                      # selected = min_command_counts_options)
                      selected = c(1000)
    )
    )
  })
  
  # Filter the summarized ratio data based on dynamic menu options
  getData <- reactive({
    filtered_data <- subset(data, declaration_count %in% input$variable_cts)
    subset(filtered_data, generated_command_min_count %in% input$command_cts)
  })
  
  # Plot the appropriate measurement
  plot <- reactive({
    data <- getData()
    if (input$measurement == 1) {
      graph <- exec_time_graph(data)
    } else {
      graph <- instr_ct_graph(data)
    }
    graph
  })
  
  # Render plot in UI
  output$plot <- renderPlot({
    plot()
  })
  
  
  # Displaying random code
  output$code_choices <- renderUI({
    selectInput("sample_program_name", label = h5("Sample Random Program Code"),
                choices = vals_to_options(get_program_names()),
                selected = -1
    )
  })
  
  # Update sample code displayed
  observe({
    input$sample_program_name  # establish dependency
    updateAceEditor(session, editorId = "code", value = get_random_program_text(input$sample_program_name))
  })
})