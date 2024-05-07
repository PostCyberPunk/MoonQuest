
namespace PCP.LibLime
{
	public class PcManager : BasePluginBride
	{
		private void Awake()
		{
			mTag = "PcManger";
		}
		protected override void OnCreate()
		{
			mPlugin.Call("fakeStart");
		}
		protected override void OnDestroy()
		{
			base.OnDestroy();
		}
	}
}

