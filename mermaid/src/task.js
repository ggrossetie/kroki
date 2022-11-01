class Task {
  constructor (source, isPng = false) {
    this.source = source
    this.isPng = isPng
    this.mermaidConfig = {
      theme: 'default',
      fontFamily: '\'Noto Sans\', sans-serif',
      class: {
        useMaxWidth: false
      },
      er: {
        useMaxWidth: false
      },
      flowchart: {
        useMaxWidth: false
      },
      gantt: {
        useMaxWidth: false
      },
      git: {
        useMaxWidth: false
      },
      journey: {
        useMaxWidth: false
      },
      sequence: {
        useMaxWidth: false
      },
      state: {
        useMaxWidth: false
      }
    }
  }
}

module.exports = Task
