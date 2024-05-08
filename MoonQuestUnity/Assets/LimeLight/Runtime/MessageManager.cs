#define DEBUGING
using System.Collections.Generic;
using TMPro;
using UnityEngine;

[RequireComponent(typeof(TextMeshProUGUI))]
public class MessageManager : MonoBehaviour
{
	private TextMeshProUGUI mTMP;
	public static MessageManager Instance { get; private set; }

	private readonly Queue<string> messageQueue = new();
	public int maxMessages = 10;
	//MonoLifeCycle-----------
	private void Awake()
	{
		if (Instance != null)
		{
			Destroy(gameObject);
			UnityEngine.Debug.LogError("MessageManager already exists in the scene");
			return;
		}
		Instance = this;

	}
	private void Start()
	{
		mTMP = GetComponent<TextMeshProUGUI>();
	}

	private void OnDestroy()
	{
		if (Instance == this)
		{
			Instance = null;
		}
	}
	//---------Messages-------------
	public void Info(string msg)
	{
		AddMessage(msg);
	}
	public void Warn(string msg)
	{
		AddMessage("<color=yellow>" + msg + "</color>");
	}
	public void Error(string msg)
	{
		AddMessage("<color=red>" + msg + "</color>");
	}
	public void Debug(string msg)
	{
#if DEBUGING
		AddMessage("<color=green>" + msg + "</color>");
#endif
	}
	private void AddMessage(string msg)
	{
		messageQueue.Enqueue(msg);
		if (messageQueue.Count > maxMessages)
		{
			messageQueue.Dequeue();
		}
		UpdateText();
	}
	//---------Text-------------
	private void UpdateText()
	{
		mTMP.text = string.Join("\n", messageQueue.ToArray());
	}
}
