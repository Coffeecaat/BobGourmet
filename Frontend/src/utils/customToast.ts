// Custom toast implementation that's isolated from React lifecycle
export const customToast = {
  error: (message: string, duration: number = 30000) => { // Increased to 30 seconds
    console.log('=== CUSTOM TOAST ERROR ===');
    console.log('Message:', message);
    console.log('Duration:', duration);
    console.log('Timestamp:', new Date().toISOString());

    // Create persistent debug logger first
    const debugId = 'debug-logger-' + Date.now();
    const debugLogger = document.createElement('div');
    debugLogger.id = debugId;
    debugLogger.style.cssText = `
      position: fixed;
      top: 100px;
      right: 20px;
      background: black;
      color: lime;
      font-family: monospace;
      font-size: 12px;
      padding: 10px;
      border: 2px solid lime;
      z-index: 200000;
      width: 300px;
      height: 200px;
      overflow-y: auto;
    `;
    
    const addDebugLog = (text: string) => {
      const timestamp = new Date().toISOString().split('T')[1].split('.')[0];
      debugLogger.innerHTML += `<div>[${timestamp}] ${text}</div>`;
      debugLogger.scrollTop = debugLogger.scrollHeight;
    };

    document.body.appendChild(debugLogger);
    addDebugLog('Debug logger started');
    addDebugLog(`Creating toast: ${message}`);

    // Create toast element
    const toastId = 'custom-error-toast-' + Date.now();
    const toast = document.createElement('div');
    toast.id = toastId;
    toast.style.cssText = `
      position: fixed;
      top: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: #DC2626;
      color: white;
      font-weight: bold;
      font-size: 18px;
      padding: 24px 32px;
      border-radius: 12px;
      border: 4px solid #EF4444;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
      z-index: 100000;
      min-width: 400px;
      max-width: 500px;
      text-align: center;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif;
      animation: slideInFromTop 0.3s ease-out;
    `;
    
    toast.textContent = message;

    // Add animation styles
    const style = document.createElement('style');
    style.textContent = `
      @keyframes slideInFromTop {
        from {
          opacity: 0;
          transform: translateX(-50%) translateY(-20px);
        }
        to {
          opacity: 1;
          transform: translateX(-50%) translateY(0);
        }
      }
      @keyframes slideOutToTop {
        from {
          opacity: 1;
          transform: translateX(-50%) translateY(0);
        }
        to {
          opacity: 0;
          transform: translateX(-50%) translateY(-20px);
        }
      }
    `;
    document.head.appendChild(style);

    // Add to DOM
    document.body.appendChild(toast);
    addDebugLog('Toast added to DOM');
    
    console.log('Custom toast element created and added to DOM');
    console.log('Toast element:', toast);

    // Check every second if toast is still there
    const checkInterval = setInterval(() => {
      const exists = document.getElementById(toastId) !== null;
      addDebugLog(`Toast exists: ${exists}`);
      if (!exists) {
        addDebugLog('Toast disappeared unexpectedly!');
        clearInterval(checkInterval);
      }
    }, 1000);

    // Auto-remove after duration
    const removeToast = () => {
      addDebugLog('Removing toast (timeout)');
      console.log('=== REMOVING CUSTOM TOAST ===');
      console.log('Time elapsed:', Date.now() - parseInt(toastId.split('-').pop() || '0'));
      
      clearInterval(checkInterval);
      toast.style.animation = 'slideOutToTop 0.3s ease-in';
      setTimeout(() => {
        if (toast.parentNode) {
          toast.parentNode.removeChild(toast);
          addDebugLog('Toast removed from DOM');
          console.log('Custom toast removed from DOM');
        }
      }, 300);
      
      // Remove debug logger after 5 more seconds
      setTimeout(() => {
        if (debugLogger.parentNode) {
          debugLogger.parentNode.removeChild(debugLogger);
        }
      }, 5000);
    };

    const timeoutId = setTimeout(removeToast, duration);
    
    // Add click to dismiss
    toast.addEventListener('click', () => {
      addDebugLog('Toast clicked - dismissing');
      clearTimeout(timeoutId);
      removeToast();
    });

    return toastId;
  }
};